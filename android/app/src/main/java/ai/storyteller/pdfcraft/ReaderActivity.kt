package ai.storyteller.pdfcraft

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ai.storyteller.pdfcraft.adapter.ContinuousPdfAdapter
import ai.storyteller.pdfcraft.adapter.ThumbnailGridAdapter
import ai.storyteller.pdfcraft.data.DocumentStorage
import ai.storyteller.pdfcraft.data.RecentDocumentManager
import ai.storyteller.pdfcraft.engine.PdfEngine
import ai.storyteller.pdfcraft.view.DrawingCanvasView
import ai.storyteller.pdfcraft.view.PdfReadingView
import ai.storyteller.pdfcraft.view.ZoomableRecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.util.Locale

class ReaderActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var recentManager: RecentDocumentManager
    private var pdfEngine: PdfEngine? = null
    private var continuousAdapter: ContinuousPdfAdapter? = null
    private var thumbnailAdapter: ThumbnailGridAdapter? = null
    private lateinit var layoutManager: LinearLayoutManager

    private lateinit var documentUri: Uri
    private var trackingUriString: String = ""
    private var documentTitle: String = "Documento"
    private var currentPageIndex: Int = 0
    private var currentScrollOffsetPx: Int = 0
    private var totalPages: Int = 0

    private var currentMode = PdfReadingView.ReadingMode.NORMAL
    private var isKeepScreenOn = false
    private var areControlsVisible = true
    private var isCropMarginsActive = false

    // TTS
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Auto-Scroll state
    private var isAutoScrolling = false
    private var isAutoScrollPaused = false
    private var autoScrollSpeed = 1.0f
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (isAutoScrolling && !isAutoScrollPaused) {
                val step = (autoScrollSpeed * 2.5f).toInt().coerceAtLeast(1)
                rvContinuousPdf.scrollBy(0, step)
                autoScrollHandler.postDelayed(this, 16)
            }
        }
    }

    // UI elements
    private lateinit var readerRoot: View
    private lateinit var rvContinuousPdf: ZoomableRecyclerView
    private lateinit var drawingCanvas: DrawingCanvasView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var readerToolbar: MaterialToolbar
    private lateinit var bottomControlCard: CardView
    private lateinit var pageSeekBar: SeekBar
    private lateinit var tvPageIndicator: TextView
    private lateinit var tvFloatingPageBadge: TextView
    private lateinit var tvBrightnessBadge: TextView

    // Fast Scroller
    private lateinit var fastScrollerContainer: View
    private lateinit var vFastScrollThumb: View
    private lateinit var tvFastScrollBubble: TextView

    // Drawing Controls
    private lateinit var cardDrawingControls: CardView
    private lateinit var btnDrawColorBlue: View
    private lateinit var btnDrawColorBlack: View
    private lateinit var btnDrawColorRed: View
    private lateinit var btnDrawUndo: ImageButton
    private lateinit var btnDrawClear: ImageButton
    private lateinit var btnDrawFinish: ImageButton

    // Thumbnail Overlay
    private lateinit var cardThumbnailOverlay: CardView
    private lateinit var rvThumbnails: RecyclerView
    private lateinit var btnThumbnailClose: ImageButton

    // TTS Controls
    private lateinit var cardTtsControls: CardView
    private lateinit var tvTtsStatus: TextView
    private lateinit var btnTtsStop: ImageButton

    // Auto Scroll Card
    private lateinit var cardAutoScrollControls: CardView
    private lateinit var btnAutoScrollSlower: ImageButton
    private lateinit var btnAutoScrollFaster: ImageButton
    private lateinit var tvAutoScrollSpeed: TextView
    private lateinit var btnAutoScrollPauseToggle: ImageButton
    private lateinit var btnAutoScrollStop: ImageButton

    // Clean Toolbar buttons
    private lateinit var btnPrevPage: ImageButton
    private lateinit var btnNextPage: ImageButton
    private lateinit var btnEnterReadingMode: Button
    private lateinit var btnQuickNightMode: ImageButton
    private lateinit var btnMoreOptions: ImageButton

    private val badgeHandler = Handler(Looper.getMainLooper())
    private val hideBadgeRunnable = Runnable {
        tvFloatingPageBadge.animate().alpha(0f).setDuration(400).withEndAction {
            tvFloatingPageBadge.visibility = View.GONE
        }.start()
    }

    private val hideBrightnessRunnable = Runnable {
        tvBrightnessBadge.animate().alpha(0f).setDuration(300).withEndAction {
            tvBrightnessBadge.visibility = View.GONE
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        recentManager = RecentDocumentManager(this)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val originalUri = intent.getStringExtra(EXTRA_ORIGINAL_URI)

        val uri = when {
            !filePath.isNullOrEmpty() && java.io.File(filePath).exists() -> Uri.fromFile(java.io.File(filePath))
            intent.data != null -> intent.data!!
            else -> run {
                Toast.makeText(this, R.string.error_opening_pdf, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        documentUri = uri
        trackingUriString = originalUri ?: documentUri.toString()
        documentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Documento.pdf"
        currentPageIndex = intent.getIntExtra(EXTRA_START_PAGE, 0)
        currentScrollOffsetPx = intent.getIntExtra(EXTRA_SCROLL_OFFSET, 0)

        // Restore saved reading mode and exact scroll position
        val existing = recentManager.getDocument(trackingUriString)
        existing?.let {
            currentPageIndex = it.lastReadPage
            currentScrollOffsetPx = it.scrollOffsetPx
            try {
                currentMode = PdfReadingView.ReadingMode.valueOf(it.readingMode)
            } catch (_: Exception) {}
        }

        // Initialize TTS
        tts = TextToSpeech(this, this)

        bindViews()
        setupListeners()
        setupBackNavigation()
        setupFastScroller()
        setupBrightnessGesture()
        setupDrawingTools()
        applyReadingColorMode(currentMode)

        loadPdfDocument()
    }

    private fun bindViews() {
        readerRoot = findViewById(R.id.readerRoot)
        rvContinuousPdf = findViewById(R.id.rvContinuousPdf)
        drawingCanvas = findViewById(R.id.drawingCanvas)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        readerToolbar = findViewById(R.id.readerToolbar)
        bottomControlCard = findViewById(R.id.bottomControlCard)
        pageSeekBar = findViewById(R.id.pageSeekBar)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        tvFloatingPageBadge = findViewById(R.id.tvFloatingPageBadge)
        tvBrightnessBadge = findViewById(R.id.tvBrightnessBadge)

        fastScrollerContainer = findViewById(R.id.fastScrollerContainer)
        vFastScrollThumb = findViewById(R.id.vFastScrollThumb)
        tvFastScrollBubble = findViewById(R.id.tvFastScrollBubble)

        cardDrawingControls = findViewById(R.id.cardDrawingControls)
        btnDrawColorBlue = findViewById(R.id.btnDrawColorBlue)
        btnDrawColorBlack = findViewById(R.id.btnDrawColorBlack)
        btnDrawColorRed = findViewById(R.id.btnDrawColorRed)
        btnDrawUndo = findViewById(R.id.btnDrawUndo)
        btnDrawClear = findViewById(R.id.btnDrawClear)
        btnDrawFinish = findViewById(R.id.btnDrawFinish)

        cardThumbnailOverlay = findViewById(R.id.cardThumbnailOverlay)
        rvThumbnails = findViewById(R.id.rvThumbnails)
        btnThumbnailClose = findViewById(R.id.btnThumbnailClose)

        cardTtsControls = findViewById(R.id.cardTtsControls)
        tvTtsStatus = findViewById(R.id.tvTtsStatus)
        btnTtsStop = findViewById(R.id.btnTtsStop)

        cardAutoScrollControls = findViewById(R.id.cardAutoScrollControls)
        btnAutoScrollSlower = findViewById(R.id.btnAutoScrollSlower)
        btnAutoScrollFaster = findViewById(R.id.btnAutoScrollFaster)
        tvAutoScrollSpeed = findViewById(R.id.tvAutoScrollSpeed)
        btnAutoScrollPauseToggle = findViewById(R.id.btnAutoScrollPauseToggle)
        btnAutoScrollStop = findViewById(R.id.btnAutoScrollStop)

        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        btnEnterReadingMode = findViewById(R.id.btnEnterReadingMode)
        btnQuickNightMode = findViewById(R.id.btnQuickNightMode)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)

        readerToolbar.title = documentTitle

        layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        rvContinuousPdf.layoutManager = layoutManager
        rvContinuousPdf.itemAnimator = null

        rvThumbnails.layoutManager = GridLayoutManager(this, 3)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (cardThumbnailOverlay.visibility == View.VISIBLE) {
                    cardThumbnailOverlay.visibility = View.GONE
                    return
                }
                if (cardDrawingControls.visibility == View.VISIBLE) {
                    exitDrawingMode()
                    return
                }
                if (cardTtsControls.visibility == View.VISIBLE) {
                    stopTts()
                    return
                }
                if (isAutoScrolling) {
                    stopAutoScroll()
                    return
                }
                if (!areControlsVisible) {
                    toggleControlsVisibility()
                    Toast.makeText(this@ReaderActivity, R.string.reading_mode_exit_tip, Toast.LENGTH_SHORT).show()
                } else {
                    saveCurrentProgress()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupListeners() {
        readerToolbar.setNavigationOnClickListener {
            saveCurrentProgress()
            finish()
        }

        // Tap on document to toggle controls / pause auto scroll
        rvContinuousPdf.onSingleTapListener = {
            if (isAutoScrolling) {
                toggleAutoScrollPause()
            } else {
                toggleControlsVisibility()
            }
        }

        // Dedicated Button: Enter Immersion Reading Mode
        btnEnterReadingMode.setOnClickListener {
            enterImmersionReadingMode()
        }

        // 1-Tap Quick Night Mode Toggle
        btnQuickNightMode.setOnClickListener {
            toggleQuickNightMode()
        }

        // Dropdown Menu: More Options
        btnMoreOptions.setOnClickListener {
            showOptionsMenu()
        }

        btnThumbnailClose.setOnClickListener {
            cardThumbnailOverlay.visibility = View.GONE
        }

        btnTtsStop.setOnClickListener {
            stopTts()
        }

        btnAutoScrollSlower.setOnClickListener {
            if (autoScrollSpeed > 0.5f) {
                autoScrollSpeed -= 0.5f
                updateAutoScrollUi()
            }
        }

        btnAutoScrollFaster.setOnClickListener {
            if (autoScrollSpeed < 5.0f) {
                autoScrollSpeed += 0.5f
                updateAutoScrollUi()
            }
        }

        btnAutoScrollPauseToggle.setOnClickListener {
            toggleAutoScrollPause()
        }

        btnAutoScrollStop.setOnClickListener {
            stopAutoScroll()
        }

        btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                jumpToPageSmooth(currentPageIndex - 1)
            }
        }

        btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                jumpToPageSmooth(currentPageIndex + 1)
            }
        }

        tvPageIndicator.setOnClickListener {
            showJumpToPageDialog()
        }

        pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && progress in 0 until totalPages) {
                    jumpToPageInstant(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        rvContinuousPdf.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && firstVisible != currentPageIndex) {
                    currentPageIndex = firstVisible
                    updateUiControls()
                    showFloatingBadge(firstVisible)
                    thumbnailAdapter?.setCurrentPage(firstVisible)
                }
                syncFastScrollThumbPosition()
            }
        })
    }

    private fun toggleQuickNightMode() {
        val nextMode = if (currentMode == PdfReadingView.ReadingMode.NIGHT) {
            PdfReadingView.ReadingMode.NORMAL
        } else {
            PdfReadingView.ReadingMode.NIGHT
        }
        applyReadingColorMode(nextMode)
        saveCurrentProgress()
        val msg = if (nextMode == PdfReadingView.ReadingMode.NIGHT) {
            "Modo Noturno (Folha preta / Texto branco)"
        } else {
            "Modo Diurno Padrão"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showOptionsMenu() {
        val cropStatus = if (isCropMarginsActive) "✂️ Recorte Inteligente (Ativo: 3mm)" else "✂️ Recorte Inteligente (Desativado)"
        val screenStatus = if (isKeepScreenOn) "💡 Manter Tela Ligada (Ativo)" else "💡 Manter Tela Ligada (Padrão)"
        val orientationStatus = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "🔄 Rotação da Tela (Retrato)"
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "🔄 Rotação da Tela (Paisagem)"
            else -> "🔄 Rotação da Tela (Automática)"
        }
        val isCurrentBookmarked = recentManager.isBookmarked(trackingUriString, currentPageIndex)
        val bookmarkTitle = if (isCurrentBookmarked) "⭐ Página Atual Marcada (Toque para desmarcar)" else "☆ Marcar Página Atual"

        val options = arrayOf(
            "🎨 Tema de Leitura (Kindle E-Ink, Sépia, etc.)",
            cropStatus,
            "🖼️ Mosaico de Miniaturas de Páginas",
            "✍️ Anotar e Assinar Documento",
            "🔊 Leitura em Voz Alta (Ouvir PDF)",
            if (isAutoScrolling) "⏸️ Parar Rolagem Automática" else "▶️ Rolagem Automática (Hands-Free)",
            bookmarkTitle,
            "📑 Ver Todos os Marcadores (${recentManager.getBookmarks(trackingUriString).size})",
            "📤 Compartilhar Documento",
            orientationStatus,
            screenStatus
        )

        AlertDialog.Builder(this)
            .setTitle("Opções do Leitor")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showReadingModeDialog()
                    1 -> toggleCropMargins()
                    2 -> openThumbnailGrid()
                    3 -> enterDrawingMode()
                    4 -> toggleTts()
                    5 -> {
                        if (isAutoScrolling) stopAutoScroll() else startAutoScroll()
                    }
                    6 -> toggleCurrentPageBookmark()
                    7 -> showBookmarksListDialog()
                    8 -> shareDocument()
                    9 -> cycleScreenOrientation()
                    10 -> toggleKeepScreenOn()
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun setupDrawingTools() {
        btnDrawColorBlue.setOnClickListener {
            drawingCanvas.currentColor = Color.parseColor("#2563EB")
            Toast.makeText(this, "Caneta Azul", Toast.LENGTH_SHORT).show()
        }
        btnDrawColorBlack.setOnClickListener {
            drawingCanvas.currentColor = Color.parseColor("#000000")
            Toast.makeText(this, "Caneta Preta", Toast.LENGTH_SHORT).show()
        }
        btnDrawColorRed.setOnClickListener {
            drawingCanvas.currentColor = Color.parseColor("#EF4444")
            Toast.makeText(this, "Caneta Vermelha", Toast.LENGTH_SHORT).show()
        }
        btnDrawUndo.setOnClickListener {
            drawingCanvas.undo()
        }
        btnDrawClear.setOnClickListener {
            drawingCanvas.clear()
        }
        btnDrawFinish.setOnClickListener {
            exitDrawingMode()
        }
    }

    private fun enterDrawingMode() {
        drawingCanvas.visibility = View.VISIBLE
        drawingCanvas.isEnabled = true
        cardDrawingControls.visibility = View.VISIBLE
        Toast.makeText(this, "Modo Desenho/Assinatura ativo. Use o dedo para assinar ou grifar.", Toast.LENGTH_SHORT).show()
    }

    private fun exitDrawingMode() {
        cardDrawingControls.visibility = View.GONE
        drawingCanvas.isEnabled = false
    }

    private fun openThumbnailGrid() {
        if (pdfEngine == null || totalPages <= 0) return
        if (thumbnailAdapter == null) {
            thumbnailAdapter = ThumbnailGridAdapter(
                engine = pdfEngine!!,
                coroutineScope = lifecycleScope,
                currentPageIndex = currentPageIndex,
                onPageSelected = { selectedPage ->
                    cardThumbnailOverlay.visibility = View.GONE
                    jumpToPageSmooth(selectedPage)
                }
            )
            rvThumbnails.adapter = thumbnailAdapter
        } else {
            thumbnailAdapter?.setCurrentPage(currentPageIndex)
        }
        cardThumbnailOverlay.visibility = View.VISIBLE
        rvThumbnails.scrollToPosition(currentPageIndex)
    }

    private fun toggleCropMargins() {
        isCropMarginsActive = !isCropMarginsActive
        continuousAdapter?.isCropMarginsEnabled = isCropMarginsActive
        if (isCropMarginsActive) {
            Toast.makeText(this, "Recorte inteligente: texto ajustado com margem de 3mm", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Margens normais da página", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("pt", "BR")
            isTtsReady = true
        }
    }

    private fun toggleTts() {
        if (!isTtsReady || tts == null) {
            Toast.makeText(this, "Sintetizador de voz carregando...", Toast.LENGTH_SHORT).show()
            return
        }
        cardTtsControls.visibility = View.VISIBLE
        val textToSpeak = "Página ${currentPageIndex + 1} de $totalPages. Documento: $documentTitle."
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        tvTtsStatus.text = "🔊 Pág. ${currentPageIndex + 1}"
    }

    private fun stopTts() {
        tts?.stop()
        cardTtsControls.visibility = View.GONE
    }

    private fun setupBrightnessGesture() {
        rvContinuousPdf.onBrightnessDeltaListener = { deltaFraction ->
            val layoutParams = window.attributes
            val currentBrightness = if (layoutParams.screenBrightness < 0f) 0.5f else layoutParams.screenBrightness
            val newBrightness = (currentBrightness + deltaFraction).coerceIn(0.02f, 1.0f)
            layoutParams.screenBrightness = newBrightness
            window.attributes = layoutParams

            val percent = (newBrightness * 100).toInt()
            tvBrightnessBadge.text = "☀️ $percent%"
            tvBrightnessBadge.visibility = View.VISIBLE
            tvBrightnessBadge.alpha = 1f

            badgeHandler.removeCallbacks(hideBrightnessRunnable)
            badgeHandler.postDelayed(hideBrightnessRunnable, 1500)
        }
    }

    private fun setupFastScroller() {
        fastScrollerContainer.setOnTouchListener { _, event ->
            if (totalPages <= 1) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val containerHeight = fastScrollerContainer.height.toFloat()
                    if (containerHeight > 0f) {
                        val touchY = event.y.coerceIn(0f, containerHeight)
                        val fraction = touchY / containerHeight
                        val targetPage = (fraction * totalPages).toInt().coerceIn(0, totalPages - 1)

                        vFastScrollThumb.translationY = touchY.coerceAtMost(containerHeight - vFastScrollThumb.height)
                        tvFastScrollBubble.translationY = touchY
                        tvFastScrollBubble.text = "Pág. ${targetPage + 1} de $totalPages"
                        tvFastScrollBubble.visibility = View.VISIBLE

                        jumpToPageInstant(targetPage)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tvFastScrollBubble.animate().alpha(0f).setDuration(300).withEndAction {
                        tvFastScrollBubble.visibility = View.GONE
                        tvFastScrollBubble.alpha = 1f
                    }.start()
                    true
                }
                else -> false
            }
        }
    }

    private fun syncFastScrollThumbPosition() {
        if (totalPages <= 1) return
        val containerHeight = fastScrollerContainer.height.toFloat()
        if (containerHeight > 0f) {
            val fraction = currentPageIndex.toFloat() / (totalPages - 1).coerceAtLeast(1).toFloat()
            val maxTravel = containerHeight - vFastScrollThumb.height.toFloat()
            vFastScrollThumb.translationY = fraction * maxTravel
        }
    }

    private fun startAutoScroll() {
        isAutoScrolling = true
        isAutoScrollPaused = false
        cardAutoScrollControls.visibility = View.VISIBLE
        btnAutoScrollPauseToggle.setImageResource(R.drawable.ic_pause)
        updateAutoScrollUi()
        autoScrollHandler.post(autoScrollRunnable)
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        isAutoScrollPaused = false
        autoScrollHandler.removeCallbacks(autoScrollRunnable)
        cardAutoScrollControls.visibility = View.GONE
    }

    private fun toggleAutoScrollPause() {
        if (!isAutoScrolling) return
        isAutoScrollPaused = !isAutoScrollPaused
        if (isAutoScrollPaused) {
            btnAutoScrollPauseToggle.setImageResource(R.drawable.ic_play_arrow)
            autoScrollHandler.removeCallbacks(autoScrollRunnable)
        } else {
            btnAutoScrollPauseToggle.setImageResource(R.drawable.ic_pause)
            autoScrollHandler.post(autoScrollRunnable)
        }
    }

    private fun updateAutoScrollUi() {
        tvAutoScrollSpeed.text = String.format("%.1fx", autoScrollSpeed)
    }

    private fun toggleCurrentPageBookmark() {
        if (totalPages <= 0) return
        val isAdded = recentManager.toggleBookmark(trackingUriString, currentPageIndex)
        val displayPage = currentPageIndex + 1
        val msg = if (isAdded) {
            getString(R.string.bookmark_added, displayPage)
        } else {
            getString(R.string.bookmark_removed, displayPage)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarksListDialog() {
        val bookmarks = recentManager.getBookmarks(trackingUriString)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }

        val items = bookmarks.map { "Página ${it + 1}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks)
            .setItems(items) { _, which ->
                val targetPage = bookmarks[which]
                jumpToPageSmooth(targetPage)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cycleScreenOrientation() {
        when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Toast.makeText(this, R.string.orientation_landscape, Toast.LENGTH_SHORT).show()
            }
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                Toast.makeText(this, R.string.orientation_auto, Toast.LENGTH_SHORT).show()
            }
            else -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Toast.makeText(this, R.string.orientation_portrait, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPdfDocument() {
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val engine = PdfEngine.open(this@ReaderActivity, documentUri)
                pdfEngine = engine
                totalPages = engine.pageCount

                if (totalPages <= 0) {
                    throw IllegalStateException("PDF contains no pages")
                }

                pageSeekBar.max = totalPages - 1
                currentPageIndex = currentPageIndex.coerceIn(0, totalPages - 1)

                val displayWidth = resources.displayMetrics.widthPixels
                continuousAdapter = ContinuousPdfAdapter(
                    engine = engine,
                    coroutineScope = lifecycleScope,
                    readingMode = currentMode
                ).apply {
                    setTargetDisplayWidth(displayWidth)
                    isCropMarginsEnabled = isCropMarginsActive
                }

                rvContinuousPdf.adapter = continuousAdapter

                layoutManager.scrollToPositionWithOffset(currentPageIndex, currentScrollOffsetPx)
                updateUiControls()

                loadingIndicator.visibility = View.GONE
                saveCurrentProgress()
            } catch (e: Exception) {
                loadingIndicator.visibility = View.GONE
                val msg = if (e is SecurityException) {
                    "A permissão de acesso deste arquivo na nuvem expirou. Por favor selecione o arquivo novamente."
                } else {
                    e.localizedMessage ?: e.message ?: "Erro ao processar o arquivo PDF selecionado."
                }
                AlertDialog.Builder(this@ReaderActivity)
                    .setTitle(R.string.error_opening_pdf)
                    .setMessage(msg)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun jumpToPageSmooth(targetIndex: Int) {
        if (targetIndex in 0 until totalPages) {
            currentPageIndex = targetIndex
            rvContinuousPdf.smoothScrollToPosition(targetIndex)
            updateUiControls()
            saveCurrentProgress()
        }
    }

    private fun jumpToPageInstant(targetIndex: Int) {
        if (targetIndex in 0 until totalPages) {
            currentPageIndex = targetIndex
            layoutManager.scrollToPositionWithOffset(targetIndex, 0)
            updateUiControls()
            saveCurrentProgress()
        }
    }

    private fun updateUiControls() {
        tvPageIndicator.text = getString(R.string.page_counter_template, currentPageIndex + 1, totalPages)
        pageSeekBar.progress = currentPageIndex
        btnPrevPage.isEnabled = currentPageIndex > 0
        btnNextPage.isEnabled = currentPageIndex < totalPages - 1
        btnPrevPage.alpha = if (currentPageIndex > 0) 1.0f else 0.35f
        btnNextPage.alpha = if (currentPageIndex < totalPages - 1) 1.0f else 0.35f
    }

    private fun showFloatingBadge(position: Int) {
        val displayPage = (position + 1).coerceIn(1, totalPages)
        tvFloatingPageBadge.text = "$displayPage / $totalPages"
        tvFloatingPageBadge.visibility = View.VISIBLE
        tvFloatingPageBadge.alpha = 1f

        badgeHandler.removeCallbacks(hideBadgeRunnable)
        badgeHandler.postDelayed(hideBadgeRunnable, 2000)
    }

    private fun enterImmersionReadingMode() {
        areControlsVisible = false
        animateControls(false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        Toast.makeText(this, "Modo Leitura ativado. Toque na tela ou no Voltar para reexibir.", Toast.LENGTH_SHORT).show()
    }

    private fun toggleControlsVisibility() {
        areControlsVisible = !areControlsVisible
        animateControls(areControlsVisible)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (areControlsVisible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun animateControls(show: Boolean) {
        if (show) {
            readerToolbar.visibility = View.VISIBLE
            bottomControlCard.visibility = View.VISIBLE
            fastScrollerContainer.visibility = View.VISIBLE
            readerToolbar.animate().alpha(1f).translationY(0f).setDuration(250).start()
            bottomControlCard.animate().alpha(1f).translationY(0f).setDuration(250).start()
            fastScrollerContainer.animate().alpha(1f).setDuration(250).start()
        } else {
            readerToolbar.animate().alpha(0f).translationY(-readerToolbar.height.toFloat()).setDuration(250).withEndAction {
                readerToolbar.visibility = View.GONE
            }.start()
            bottomControlCard.animate().alpha(0f).translationY(bottomControlCard.height.toFloat() + 50f).setDuration(250).withEndAction {
                bottomControlCard.visibility = View.GONE
            }.start()
            fastScrollerContainer.animate().alpha(0.2f).setDuration(250).start()
        }
    }

    private fun showJumpToPageDialog() {
        if (totalPages <= 1) return

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 - $totalPages"
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.jump_to_page)
            .setMessage(getString(R.string.enter_page_number, totalPages))
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val pageStr = input.text.toString().trim()
                val pageNum = pageStr.toIntOrNull()
                if (pageNum != null && pageNum in 1..totalPages) {
                    jumpToPageSmooth(pageNum - 1)
                } else {
                    Toast.makeText(this, R.string.page_not_found, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showReadingModeDialog() {
        val modes = arrayOf(
            getString(R.string.mode_normal),
            getString(R.string.mode_kindle_eink),
            getString(R.string.mode_night),
            getString(R.string.mode_sepia)
        )
        val selectedIndex = currentMode.ordinal

        AlertDialog.Builder(this)
            .setTitle(R.string.reading_mode)
            .setSingleChoiceItems(modes, selectedIndex) { dialog, which ->
                val newMode = PdfReadingView.ReadingMode.values()[which]
                applyReadingColorMode(newMode)
                saveCurrentProgress()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyReadingColorMode(mode: PdfReadingView.ReadingMode) {
        currentMode = mode
        continuousAdapter?.setReadingMode(mode)

        when (mode) {
            PdfReadingView.ReadingMode.NORMAL -> {
                readerRoot.setBackgroundColor(getColor(R.color.reading_normal_bg))
                bottomControlCard.setCardBackgroundColor(Color.parseColor("#F2FFFFFF"))
                readerToolbar.setBackgroundColor(Color.parseColor("#E60F172A"))
                btnQuickNightMode.setImageResource(R.drawable.ic_dark_mode)
                btnQuickNightMode.setColorFilter(getColor(R.color.on_primary))

                // Bottom bar text & controls
                tvPageIndicator.setTextColor(Color.parseColor("#0F172A"))
                btnPrevPage.setColorFilter(Color.parseColor("#0F172A"))
                btnNextPage.setColorFilter(Color.parseColor("#0F172A"))
                val accentColor = getColor(R.color.accent)
                pageSeekBar.thumbTintList = ColorStateList.valueOf(accentColor)
                pageSeekBar.progressTintList = ColorStateList.valueOf(accentColor)
                pageSeekBar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
            }
            PdfReadingView.ReadingMode.KINDLE_EINK -> {
                readerRoot.setBackgroundColor(Color.parseColor("#E8E8E4"))
                bottomControlCard.setCardBackgroundColor(Color.parseColor("#F2F4F4F0"))
                readerToolbar.setBackgroundColor(Color.parseColor("#E627272A"))
                btnQuickNightMode.setImageResource(R.drawable.ic_dark_mode)
                btnQuickNightMode.setColorFilter(getColor(R.color.on_primary))

                // Bottom bar text & controls (monochrome e-ink aesthetic)
                tvPageIndicator.setTextColor(Color.parseColor("#18181B"))
                btnPrevPage.setColorFilter(Color.parseColor("#18181B"))
                btnNextPage.setColorFilter(Color.parseColor("#18181B"))
                val einkControl = Color.parseColor("#3F3F46")
                pageSeekBar.thumbTintList = ColorStateList.valueOf(einkControl)
                pageSeekBar.progressTintList = ColorStateList.valueOf(einkControl)
                pageSeekBar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#D4D4D8"))
            }
            PdfReadingView.ReadingMode.NIGHT -> {
                readerRoot.setBackgroundColor(Color.BLACK)
                bottomControlCard.setCardBackgroundColor(Color.parseColor("#F2121212"))
                readerToolbar.setBackgroundColor(Color.parseColor("#E6000000"))
                btnQuickNightMode.setImageResource(R.drawable.ic_brightness)
                btnQuickNightMode.setColorFilter(getColor(R.color.accent_light))

                // Bottom bar text & controls in Night Mode: Crisp White & Sky Blue accent
                tvPageIndicator.setTextColor(Color.WHITE)
                btnPrevPage.setColorFilter(Color.WHITE)
                btnNextPage.setColorFilter(Color.WHITE)
                val nightAccent = Color.parseColor("#38BDF8")
                pageSeekBar.thumbTintList = ColorStateList.valueOf(nightAccent)
                pageSeekBar.progressTintList = ColorStateList.valueOf(nightAccent)
                pageSeekBar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
            }
            PdfReadingView.ReadingMode.SEPIA -> {
                readerRoot.setBackgroundColor(getColor(R.color.reading_sepia_bg))
                bottomControlCard.setCardBackgroundColor(Color.parseColor("#F2FCF6EB"))
                readerToolbar.setBackgroundColor(Color.parseColor("#E62C2416"))
                btnQuickNightMode.setImageResource(R.drawable.ic_dark_mode)
                btnQuickNightMode.setColorFilter(getColor(R.color.on_primary))

                // Bottom bar text & controls (warm sepia aesthetic)
                tvPageIndicator.setTextColor(Color.parseColor("#3E2723"))
                btnPrevPage.setColorFilter(Color.parseColor("#3E2723"))
                btnNextPage.setColorFilter(Color.parseColor("#3E2723"))
                val sepiaAccent = Color.parseColor("#8D6E63")
                pageSeekBar.thumbTintList = ColorStateList.valueOf(sepiaAccent)
                pageSeekBar.progressTintList = ColorStateList.valueOf(sepiaAccent)
                pageSeekBar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            }
        }
    }

    private fun toggleKeepScreenOn() {
        isKeepScreenOn = !isKeepScreenOn
        if (isKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(this, R.string.screen_on_enabled, Toast.LENGTH_SHORT).show()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(this, R.string.screen_on_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDocument() {
        try {
            val localFile = DocumentStorage.getLocalFile(this, trackingUriString)
                ?: DocumentStorage.savePdfLocally(this, documentUri)

            if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                val authority = "${applicationContext.packageName}.fileprovider"
                val shareUri = FileProvider.getUriForFile(this, authority, localFile)

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Compartilhar $documentTitle"))
            } else {
                Toast.makeText(this, "Não foi possível preparar o documento para envio.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao compartilhar arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCurrentProgress() {
        if (totalPages > 0) {
            val firstPos = layoutManager.findFirstVisibleItemPosition()
            val firstView = if (firstPos != RecyclerView.NO_POSITION) layoutManager.findViewByPosition(firstPos) else null
            val offset = firstView?.top ?: 0
            val page = if (firstPos != RecyclerView.NO_POSITION) firstPos else currentPageIndex

            recentManager.saveOrUpdate(
                uriString = trackingUriString,
                title = documentTitle,
                pageCount = totalPages,
                lastReadPage = page,
                readingMode = currentMode.name,
                scrollOffsetPx = offset
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
        stopAutoScroll()
        stopTts()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentProgress()
        stopAutoScroll()
        stopTts()
        tts?.shutdown()
        tts = null
        badgeHandler.removeCallbacks(hideBadgeRunnable)
        badgeHandler.removeCallbacks(hideBrightnessRunnable)
        pdfEngine?.close()
        pdfEngine = null
    }

    companion object {
        const val EXTRA_TITLE = "extra_document_title"
        const val EXTRA_START_PAGE = "extra_start_page"
        const val EXTRA_SCROLL_OFFSET = "extra_scroll_offset"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_ORIGINAL_URI = "extra_original_uri"
    }
}
