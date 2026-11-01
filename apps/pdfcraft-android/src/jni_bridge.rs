use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jfloat, jfloatArray, jint, jlong};

use crate::{close_document, document_page_count, document_page_dimensions, open_document, render_document_page};

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativeVersion(_env: JNIEnv, _class: JClass) -> jint {
    15
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativeOpen(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
    password: JString,
) -> jlong {
    let data = match env.convert_byte_array(&bytes) {
        Ok(d) => d,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", format!("{e}"));
            return 0;
        }
    };

    let pw_str = if !password.is_null() {
        match env.get_string(&password) {
            Ok(s) => match s.to_str() {
                Ok(valid_str) => Some(valid_str.to_string()),
                Err(_) => None,
            },
            Err(_) => None,
        }
    } else {
        None
    };

    match open_document(data, pw_str.as_deref()) {
        Ok(id) => id as jlong,
        Err(e) => {
            let _ = env.throw_new("java/io/IOException", format!("{e}"));
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativeClose(_env: JNIEnv, _class: JClass, handle: jlong) {
    if handle > 0 {
        let _ = close_document(handle as u64);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativePageCount(mut env: JNIEnv, _class: JClass, handle: jlong) -> jint {
    if handle <= 0 {
        return -1;
    }
    match document_page_count(handle as u64) {
        Ok(count) => count as jint,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", format!("{e}"));
            -1
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativePageDimensions(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
) -> jfloatArray {
    if handle <= 0 || page_index < 0 {
        return std::ptr::null_mut();
    }
    match document_page_dimensions(handle as u64, page_index as usize) {
        Ok(dim) => match env.new_float_array(2) {
            Ok(arr) => {
                let dims = [dim.width, dim.height];
                if env.set_float_array_region(&arr, 0, &dims).is_ok() { arr.into_raw() } else { std::ptr::null_mut() }
            }
            Err(_) => std::ptr::null_mut(),
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/IndexOutOfBoundsException", format!("{e}"));
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativeRenderPageToBytes(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
    scale: jfloat,
) -> jni::sys::jbyteArray {
    if handle <= 0 || page_index < 0 || scale <= 0.0 {
        return std::ptr::null_mut();
    }
    match render_document_page(handle as u64, page_index as usize, scale) {
        Ok(bitmap) => match env.byte_array_from_slice(&bitmap.rgba) {
            Ok(arr) => arr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("{e}"));
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_PdfCraftCore_nativeRenderPageInto(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
    scale: jfloat,
    out_buffer: JByteArray,
) -> jboolean {
    if handle <= 0 || page_index < 0 || scale <= 0.0 || out_buffer.is_null() {
        return JNI_FALSE;
    }

    let bitmap = match render_document_page(handle as u64, page_index as usize, scale) {
        Ok(b) => b,
        Err(_) => return JNI_FALSE,
    };

    let len = match env.get_array_length(&out_buffer) {
        Ok(l) => l as usize,
        Err(_) => return JNI_FALSE,
    };

    if len < bitmap.rgba.len() {
        return JNI_FALSE;
    }

    let i8_slice: &[i8] = bytemuck::cast_slice(&bitmap.rgba);
    if env.set_byte_array_region(&out_buffer, 0, i8_slice).is_ok() { JNI_TRUE } else { JNI_FALSE }
}
