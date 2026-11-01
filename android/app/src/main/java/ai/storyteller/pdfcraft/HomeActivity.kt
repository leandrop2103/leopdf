package ai.storyteller.pdfcraft

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ai.storyteller.pdfcraft.adapter.RecentDocumentsAdapter
import ai.storyteller.pdfcraft.data.DocumentStorage
import ai.storyteller.pdfcraft.data.RecentDocumentManager
import ai.storyteller.pdfcraft.model.RecentDocument
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var recentManager: RecentDocumentManager
    private lateinit var adapter: RecentDocumentsAdapter

    private lateinit var rvRecents: RecyclerView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var fabOpen: ExtendedFloatingActionButton
    private lateinit var btnToolbarOpen: Button
    private lateinit var btnEmptyOpen: Button
    private lateinit var etSearchRecents: EditText
    private lateinit var searchLayout: View

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onPdfSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        recentManager = RecentDocumentManager(this)

        rvRecents = findViewById(R.id.rvRecents)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        fabOpen = findViewById(R.id.fabOpen)
        btnToolbarOpen = findViewById(R.id.btnToolbarOpen)
        btnEmptyOpen = findViewById(R.id.btnEmptyOpen)
        etSearchRecents = findViewById(R.id.etSearchRecents)
        searchLayout = findViewById(R.id.searchLayout)

        setupRecyclerView()
        setupListeners()

        // Handle intent if launched directly with a PDF file (e.g. from WhatsApp, Files, etc.)
        intent?.data?.let { uri ->
            onPdfSelected(uri)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRecentsList()
    }

    private fun setupRecyclerView() {
        adapter = RecentDocumentsAdapter(
            onItemClick = { doc ->
                openReader(Uri.parse(doc.uriString), doc.title, doc.lastReadPage)
            },
            onDeleteClick = { doc ->
                recentManager.removeDocument(doc.uriString)
                DocumentStorage.deleteLocalCopy(this, doc.uriString)
                refreshRecentsList()
            }
        )
        rvRecents.layoutManager = LinearLayoutManager(this)
        rvRecents.adapter = adapter
    }

    private fun setupListeners() {
        val openPicker = {
            openDocumentLauncher.launch(arrayOf("application/pdf"))
        }

        fabOpen.setOnClickListener { openPicker() }
        btnToolbarOpen.setOnClickListener { openPicker() }
        btnEmptyOpen.setOnClickListener { openPicker() }

        etSearchRecents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun refreshRecentsList() {
        val recents = recentManager.getRecentDocuments()
        adapter.updateAllDocuments(recents)

        if (recents.isEmpty()) {
            rvRecents.visibility = View.GONE
            searchLayout.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
        } else {
            rvRecents.visibility = View.VISIBLE
            searchLayout.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
        }
    }

    private fun onPdfSelected(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Some file providers do not support persistable permissions
        }

        val title = queryDocumentName(uri)
        val existingDoc = recentManager.getDocument(uri.toString())
        val startPage = existingDoc?.lastReadPage ?: 0

        // Save a permanent offline copy asynchronously so cloud permission expiry never revokes reading
        lifecycleScope.launch(Dispatchers.IO) {
            DocumentStorage.savePdfLocally(this@HomeActivity, uri)
        }

        openReader(uri, title, startPage)
    }

    private fun queryDocumentName(uri: Uri): String {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment ?: "Documento.pdf"
    }

    private fun openReader(uri: Uri, title: String, startPage: Int = 0) {
        val localFile = DocumentStorage.getLocalFile(this, uri.toString())

        val intent = Intent(this, ReaderActivity::class.java).apply {
            if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                putExtra(ReaderActivity.EXTRA_FILE_PATH, localFile.absolutePath)
                data = Uri.fromFile(localFile)
            } else {
                data = uri
                if (uri.scheme == "content") {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            putExtra(ReaderActivity.EXTRA_TITLE, title)
            putExtra(ReaderActivity.EXTRA_START_PAGE, startPage)
            putExtra(ReaderActivity.EXTRA_ORIGINAL_URI, uri.toString())
        }

        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            // Permission expired or revoked by remote cloud provider (OneDrive, Google Drive, etc.)
            showPermissionExpiredDialog(uri, title)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir o documento: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPermissionExpiredDialog(uri: Uri, title: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_opening_pdf)
            .setMessage("O aplicativo de nuvem (OneDrive/Drive) revogou o acesso temporário a '$title'.\n\nDeseja localizá-lo novamente?")
            .setPositiveButton("Localizar Arquivo") { _, _ ->
                openDocumentLauncher.launch(arrayOf("application/pdf"))
            }
            .setNegativeButton("Remover dos Recentes") { _, _ ->
                recentManager.removeDocument(uri.toString())
                DocumentStorage.deleteLocalCopy(this, uri.toString())
                refreshRecentsList()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
