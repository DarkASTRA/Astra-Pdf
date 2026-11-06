package com.astra.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.pdf.ui.screens.ReaderScreen
import com.astra.pdf.ui.theme.AstraPDFTheme
import com.astra.pdf.viewmodel.DocumentUiState
import com.astra.pdf.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null || viewModel.uiState.value is DocumentUiState.Idle) {
            handleIncomingIntent(intent)
        }

        setContent {
            AstraPDFTheme {
                val uiState by viewModel.uiState.collectAsState()
                val pageRotations by viewModel.pageRotations.collectAsState()
                val isFindActive by viewModel.isFindActive.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()
                val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
                val isSearching by viewModel.isSearching.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = uiState) {
                        is DocumentUiState.Success -> {
                            ReaderScreen(
                                document = state.document,
                                pageRotations = pageRotations,
                                onRotatePage = { pageIdx ->
                                    viewModel.rotateCurrentPage(pageIdx)
                                },
                                isFindActive = isFindActive,
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                currentMatchIndex = currentMatchIndex,
                                isSearching = isSearching,
                                onOpenFind = { viewModel.openFind() },
                                onCloseFind = { viewModel.closeFind() },
                                onQueryChange = { q -> viewModel.performSearch(q) },
                                onNextMatch = { viewModel.nextMatch() },
                                onPrevMatch = { viewModel.prevMatch() },
                                onRenderPage = { pageIdx, scale, rot ->
                                    viewModel.renderPage(pageIdx, scale, rot)
                                },
                                onCloseActivity = {
                                    finish()
                                }
                            )
                        }

                        is DocumentUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        is DocumentUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.error_open_pdf),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = state.message,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = { finish() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Text(stringResource(R.string.action_close))
                                        }
                                    }
                                }
                            }
                        }

                        is DocumentUiState.Idle -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.error_no_pdf_provided),
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = { finish() }
                                        ) {
                                            Text(stringResource(R.string.action_close))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                intent.clipData?.let { clipData ->
                    if (clipData.itemCount > 0) clipData.getItemAt(0).uri else null
                } ?: run {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> intent.data
        }

        if (uri != null) {
            viewModel.openDocumentUri(uri)
        }
    }
}
