//! pdfcraft-android — Android bridge and headless engine services.
//!
//! Provides a safe, memory-bounded, thread-safe interface for Android applications
//! (Kotlin, Java, or NativeActivity) to inspect, render, and manipulate PDF documents.

#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

pub mod jni_bridge;

use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use serde::{Deserialize, Serialize};

/// Errors surfaced to callers on Android.
#[derive(Debug, thiserror::Error)]
pub enum AndroidError {
    #[error("failed to open PDF: {0}")]
    Open(String),
    #[error("page index {0} out of bounds (total {1})")]
    PageOutOfBounds(usize, usize),
    #[error("render error: {0}")]
    Render(String),
    #[error("document not found: {0}")]
    NotFound(u64),
    #[error("invalid scale factor {0}")]
    InvalidScale(f32),
    #[error("buffer capacity insufficient: need {0} bytes, provided {1}")]
    BufferTooSmall(usize, usize),
    #[error("internal lock poisoned: {0}")]
    LockPoisoned(String),
}

/// A rendered page raster in RGBA8888 byte format suitable for Android `Bitmap.createBitmap`.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RenderedBitmap {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

/// Page dimensions in points (1/72 inch).
#[derive(Clone, Copy, Debug, Serialize, Deserialize)]
pub struct PageDimensions {
    pub width: f32,
    pub height: f32,
}

/// Loaded document in memory.
pub struct AndroidDocument {
    bytes: Arc<Vec<u8>>,
    info: pdfcraft_render::DocInfo,
    renderer: Mutex<pdfcraft_render::PageRenderer>,
}

impl AndroidDocument {
    /// Opens a PDF document from an in-memory byte vector.
    pub fn open(bytes: Vec<u8>, password: Option<&str>) -> Result<Self, AndroidError> {
        let shared_bytes = Arc::new(bytes);
        let info = pdfcraft_render::inspect(shared_bytes.clone(), password).map_err(|e| AndroidError::Open(e.to_string()))?;
        let config = pdfcraft_render::RenderConfig { password: password.map(Arc::from), ..Default::default() };
        let renderer = pdfcraft_render::PageRenderer::new(shared_bytes.clone(), config);
        Ok(Self { bytes: shared_bytes, info, renderer: Mutex::new(renderer) })
    }

    /// Number of pages in the document.
    pub fn page_count(&self) -> usize {
        self.info.pages.len()
    }

    /// Dimensions of the given page (0-indexed) in points.
    pub fn page_dimensions(&self, page: usize) -> Result<PageDimensions, AndroidError> {
        let p = self.info.pages.get(page).ok_or_else(|| AndroidError::PageOutOfBounds(page, self.page_count()))?;
        Ok(PageDimensions { width: p.width, height: p.height })
    }

    /// Renders a page (0-indexed) at the given scale factor to RGBA8888 bytes.
    pub fn render_page(&self, page: usize, scale: f32) -> Result<RenderedBitmap, AndroidError> {
        if !(scale.is_finite() && scale > 0.0 && scale <= 10.0) {
            return Err(AndroidError::InvalidScale(scale));
        }
        if page >= self.page_count() {
            return Err(AndroidError::PageOutOfBounds(page, self.page_count()));
        }

        let mut renderer = self.renderer.lock().map_err(|_| AndroidError::LockPoisoned("renderer lock poisoned".to_string()))?;
        let req = pdfcraft_render::RenderRequest { page, scale, kind: pdfcraft_render::RequestKind::Pixels, preview: false, tile: None, tag: 0 };
        let rendered = renderer.render(req);

        if let Some(err) = rendered.error {
            return Err(AndroidError::Render(err));
        }

        let rgba = rendered.rgba.to_vec();
        Ok(RenderedBitmap { width: rendered.width, height: rendered.height, rgba })
    }

    /// Renders a page directly into a pre-allocated pixel buffer.
    pub fn render_page_into(&self, page: usize, scale: f32, out: &mut [u8]) -> Result<(u32, u32), AndroidError> {
        let bitmap = self.render_page(page, scale)?;
        if out.len() < bitmap.rgba.len() {
            return Err(AndroidError::BufferTooSmall(bitmap.rgba.len(), out.len()));
        }
        if let Some(dest) = out.get_mut(..bitmap.rgba.len()) {
            dest.copy_from_slice(&bitmap.rgba);
        }
        Ok((bitmap.width, bitmap.height))
    }

    /// Document title if present in metadata.
    pub fn title(&self) -> Option<String> {
        self.info.title.clone()
    }

    /// Document author if present in metadata.
    pub fn author(&self) -> Option<String> {
        self.info.author.clone()
    }

    /// Document raw bytes.
    pub fn bytes(&self) -> &[u8] {
        &self.bytes
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn pdfcraft_version() -> u32 {
    15
}

// Global handle registry for cross-language JNI interop
static REGISTRY: OnceLock<Mutex<HashMap<u64, AndroidDocument>>> = OnceLock::new();
static NEXT_ID: AtomicU64 = AtomicU64::new(1);

fn registry() -> Result<&'static Mutex<HashMap<u64, AndroidDocument>>, AndroidError> {
    Ok(REGISTRY.get_or_init(|| Mutex::new(HashMap::new())))
}

/// Opens a document and registers it under a unique ID.
pub fn open_document(bytes: Vec<u8>, password: Option<&str>) -> Result<u64, AndroidError> {
    let doc = AndroidDocument::open(bytes, password)?;
    let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
    let reg = registry()?;
    let mut map = reg.lock().map_err(|_| AndroidError::LockPoisoned("registry lock poisoned".to_string()))?;
    map.insert(id, doc);
    Ok(id)
}

/// Closes a document and frees its resources.
pub fn close_document(id: u64) -> Result<(), AndroidError> {
    let reg = registry()?;
    let mut map = reg.lock().map_err(|_| AndroidError::LockPoisoned("registry lock poisoned".to_string()))?;
    map.remove(&id).ok_or(AndroidError::NotFound(id))?;
    Ok(())
}

/// Runs a closure with a reference to the registered document.
pub fn with_document<T, F: FnOnce(&AndroidDocument) -> Result<T, AndroidError>>(id: u64, f: F) -> Result<T, AndroidError> {
    let reg = registry()?;
    let map = reg.lock().map_err(|_| AndroidError::LockPoisoned("registry lock poisoned".to_string()))?;
    let doc = map.get(&id).ok_or(AndroidError::NotFound(id))?;
    f(doc)
}

/// Renders a page of the registered document to RGBA bytes.
pub fn render_document_page(id: u64, page: usize, scale: f32) -> Result<RenderedBitmap, AndroidError> {
    with_document(id, |doc| doc.render_page(page, scale))
}

/// Gets page count of the registered document.
pub fn document_page_count(id: u64) -> Result<usize, AndroidError> {
    with_document(id, |doc| Ok(doc.page_count()))
}

/// Gets page dimensions of the registered document.
pub fn document_page_dimensions(id: u64, page: usize) -> Result<PageDimensions, AndroidError> {
    with_document(id, |doc| doc.page_dimensions(page))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_pdf() -> Vec<u8> {
        pdfcraft_testkit::Pdf::new()
            .obj("<< /Type /Catalog /Pages 2 0 R >>")
            .obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            .obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] >>")
            .build()
    }

    #[test]
    fn open_and_inspect_minimal_pdf() {
        let pdf = sample_pdf();
        let doc = AndroidDocument::open(pdf, None).expect("sample pdf must open");
        assert_eq!(doc.page_count(), 1);
        let dim = doc.page_dimensions(0).expect("first page dimensions");
        assert!(dim.width > 0.0);
        assert!(dim.height > 0.0);
    }

    #[test]
    fn render_minimal_pdf_page() {
        let pdf = sample_pdf();
        let doc = AndroidDocument::open(pdf, None).expect("sample pdf must open");
        let bitmap = doc.render_page(0, 1.0).expect("page render must succeed");
        assert!(bitmap.width > 0);
        assert!(bitmap.height > 0);
        assert_eq!(bitmap.rgba.len(), (bitmap.width as usize) * (bitmap.height as usize) * 4);
    }

    #[test]
    fn registry_lifecycle() {
        let pdf = sample_pdf();
        let id = open_document(pdf, None).expect("document opened into registry");
        let count = document_page_count(id).expect("page count");
        assert_eq!(count, 1);
        let dim = document_page_dimensions(id, 0).expect("page dimensions");
        assert!(dim.width > 0.0);
        let bitmap = render_document_page(id, 0, 0.5).expect("page render");
        assert!(bitmap.width > 0);
        close_document(id).expect("document closed");
        assert!(document_page_count(id).is_err());
    }

    #[test]
    fn invalid_scale_and_bounds_fail_gracefully() {
        let pdf = sample_pdf();
        let doc = AndroidDocument::open(pdf, None).expect("sample pdf must open");
        assert!(doc.render_page(0, -1.0).is_err());
        assert!(doc.render_page(0, f32::NAN).is_err());
        assert!(doc.render_page(99, 1.0).is_err());
    }
}
