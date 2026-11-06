package com.astra.pdf.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.pdf.R
import com.astra.pdf.bridge.AstraNativeBridge
import com.astra.pdf.model.LoadedDocument
import com.astra.pdf.ui.PageFitCalculator
import com.astra.pdf.ui.components.ZoomableBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: LoadedDocument,
    pageRotations: Map<Int, Int>,
    onRotatePage: (Int) -> Unit,
    isFindActive: Boolean,
    searchQuery: String,
    searchResults: List<AstraNativeBridge.SearchResult>,
    currentMatchIndex: Int,
    isSearching: Boolean,
    onOpenFind: () -> Unit,
    onCloseFind: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onRenderPage: suspend (pageIndex: Int, scale: Float, rotationDegrees: Int) -> Bitmap?,
    onCloseActivity: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        if (showMenu) {
            showMenu = false
        } else if (isFindActive) {
            onCloseFind()
        } else {
            onCloseActivity()
        }
    }

    val currentVisiblePage by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentMatchIndex, searchResults) {
        if (isFindActive && searchResults.isNotEmpty() && currentMatchIndex in searchResults.indices) {
            val targetPage = searchResults[currentMatchIndex].pageIndex
            coroutineScope.launch {
                listState.animateScrollToItem(targetPage)
            }
        }
    }

    LaunchedEffect(isFindActive) {
        if (isFindActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            if (isFindActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCloseFind) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.search_close),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onQueryChange,
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_placeholder),
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (searchQuery.isNotBlank()) {
                            Text(
                                text = if (searchResults.isNotEmpty()) {
                                    stringResource(
                                        R.string.search_matches_count,
                                        currentMatchIndex + 1,
                                        searchResults.size
                                    )
                                } else {
                                    stringResource(R.string.search_no_matches)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        IconButton(
                            onClick = onPrevMatch,
                            enabled = searchResults.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.search_prev)
                            )
                        }

                        IconButton(
                            onClick = onNextMatch,
                            enabled = searchResults.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.search_next)
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = document.name,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onCloseActivity) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_menu),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rotate)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRotatePage(currentVisiblePage)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_find)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenFind()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val viewportWidthDp = maxWidth.value
            val viewportHeightDp = maxHeight.value

            ZoomableBox(
                modifier = Modifier.fillMaxSize(),
                onScroll = { delta ->
                    coroutineScope.launch {
                        listState.dispatchRawDelta(-delta)
                    }
                }
            ) {
                if (document.pageCount == 1) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val dimension = document.pageDimensions.getOrNull(0) ?: Pair(595f, 842f)
                        val rotation = pageRotations[0] ?: 0
                        PdfPageView(
                            pageIndex = 0,
                            dimension = dimension,
                            rotationDegrees = rotation,
                            viewportWidthDp = viewportWidthDp,
                            viewportHeightDp = viewportHeightDp,
                            onRender = onRenderPage
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(document.pageCount) { pageIndex ->
                            val dimension = document.pageDimensions.getOrNull(pageIndex) ?: Pair(595f, 842f)
                            val rotation = pageRotations[pageIndex] ?: 0
                            PdfPageView(
                                pageIndex = pageIndex,
                                dimension = dimension,
                                rotationDegrees = rotation,
                                viewportWidthDp = viewportWidthDp,
                                viewportHeightDp = viewportHeightDp,
                                onRender = onRenderPage
                            )
                        }
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.page_indicator,
                        currentVisiblePage + 1,
                        document.pageCount
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPageView(
    pageIndex: Int,
    dimension: Pair<Float, Float>,
    rotationDegrees: Int,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    onRender: suspend (pageIndex: Int, scale: Float, rotationDegrees: Int) -> Bitmap?
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val layout = remember(dimension, rotationDegrees, viewportWidthDp, viewportHeightDp) {
        PageFitCalculator.calculateFit(
            pageWidthPt = dimension.first,
            pageHeightPt = dimension.second,
            rotationDegrees = rotationDegrees,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            marginHorizontalDp = 16f,
            marginVerticalDp = 16f
        )
    }

    LaunchedEffect(pageIndex, rotationDegrees) {
        isLoading = true
        bitmap = onRender(pageIndex, 1.5f, rotationDegrees)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .width(layout.widthDp.dp)
            .height(layout.heightDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}
