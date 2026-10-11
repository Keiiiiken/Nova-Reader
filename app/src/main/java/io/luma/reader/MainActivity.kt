package io.luma.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.luma.reader.ui.LumaApp
import io.luma.reader.ui.theme.LumaTheme

class MainActivity : ComponentActivity() {
    private var narrationRequest by androidx.compose.runtime.mutableStateOf<String?>(null)
    private val viewModel: MainViewModel by viewModels()

    override fun onStop() {
        viewModel.flushPersistence()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        narrationRequest = intent.getStringExtra("narrationBook")
        importIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun importIntent(intent: android.content.Intent?) {
        if (intent?.action !in setOf(android.content.Intent.ACTION_VIEW, android.content.Intent.ACTION_SEND, android.content.Intent.ACTION_SEND_MULTIPLE)) return
        val uris = buildList<android.net.Uri> {
            intent?.data?.let(::add)
            intent?.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)?.let(::add)
            intent?.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)?.let(::addAll)
            intent?.clipData?.let { clip -> repeat(clip.itemCount) { clip.getItemAt(it).uri?.let(::add) } }
        }.distinct().filter { it.scheme == "content" }
        if (uris.isNotEmpty()) viewModel.importBooks(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        narrationRequest = intent.getStringExtra("narrationBook")
        if (savedInstanceState == null) importIntent(intent)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(state.loading, narrationRequest) {
                if (!state.loading) narrationRequest?.let { id -> viewModel.openBook(id); narrationRequest = null; intent.removeExtra("narrationBook") }
            }
            LumaTheme(state.library.appSettings) {
                LumaApp(viewModel)
            }
        }
    }
}
