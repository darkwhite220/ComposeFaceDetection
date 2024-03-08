package com.darkwhite.facedetectionmlkit

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Rect
import android.graphics.RectF
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.google.mlkit.vision.face.Face
import kotlin.math.ceil


@Composable
fun FaceDetectionTakeVideoScreenContent(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
) {
    val density = LocalDensity.current
    val dpToPx = remember { with(density) { 1.dp.toPx() } }
    
    var faces by remember { mutableStateOf(FacesData(emptyList())) }
    var imageRect by remember { mutableStateOf(Rect()) }
    
    val onFaceDetected = { facesList: List<Face?>, rect: Rect ->
        faces = FacesData(facesList)
        imageRect = rect
    }
    
    FaceDetectionUiContent(
        modifier = modifier,
        faces = { faces },
        imageRect = { imageRect },
        dpToPx = dpToPx,
        onFaceDetected = onFaceDetected,
        onBackClick = onBackClick,
    )
    
}

@SuppressLint("MissingPermission")
@Composable
private fun FaceDetectionUiContent(
    modifier: Modifier = Modifier,
    faces: () -> FacesData,
    imageRect: () -> Rect,
    dpToPx: Float,
    onFaceDetected: (List<Face?>, Rect) -> Unit,
    onBackClick: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { ContextCompat.getMainExecutor(context) }
    var closeCamera by remember { mutableStateOf(false) }
    
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
    }
    
    val videoCapture = remember {
        VideoCapture.Builder(recorder)
            .setMirrorMode(MIRROR_MODE_ON_FRONT_ONLY)
            .build()
    }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setImageQueueDepth(10)
                .build()
                .apply {
                    setAnalyzer(
                        executor, FaceAnalyzer(
                            onFaceDetected = onFaceDetected
                        )
                    )
                }
            
            val previewView = PreviewView(context)
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        videoCapture,
                    )
                }, executor
            )
            previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            previewView
        },
    )
    
    BackButton(onBackClick = { closeCamera = true })
    
    DrawBoxes(
        dpToPx = dpToPx,
        imageRect = imageRect,
        faces = faces
    )
    
    val contentValues = remember {
        ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Test_video.mp4")
        }
    }
    val mediaStoreOutput = remember {
        MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()
    }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    
    TakeVideoButton(
        isRecording = { currentRecording != null },
        onClick = {
            currentRecording = if (currentRecording == null) {
                videoCapture.output
                    .prepareRecording(context, mediaStoreOutput)
                    .apply { withAudioEnabled() }
                    .start(executor, captureListener)
            } else {
                currentRecording!!.stop()
                null
            }
        }
    )
    
    LaunchedEffect(key1 = closeCamera) {
        if (closeCamera) {
            currentRecording?.let {
                it.stop()
                currentRecording = null
            }
            cameraProviderFuture.get().unbindAll()
            onBackClick()
        }
    }
}

private val captureListener = Consumer<VideoRecordEvent> { event ->
    if (event !is VideoRecordEvent.Status) {
        println("EVENT: $event")
    }
}

@Composable
private fun TakeVideoButton(
    isRecording: () -> Boolean,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text(text = if (isRecording()) "Stop Recording" else "Start Recording")
        }
    }
}

@Composable
private fun DrawBoxes(
    dpToPx: Float,
    imageRect: () -> Rect,
    faces: () -> FacesData
) {
    val borderSize by remember { mutableFloatStateOf(dpToPx * 2) }
    
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val imageRectWidth by remember(imageRect()) { derivedStateOf { imageRect().width().toFloat() } }
    val imageRectHeight by remember(imageRect()) {
        derivedStateOf { imageRect().height().toFloat() }
    }
    val scaleX by remember(canvasSize.width, imageRectHeight) {
        derivedStateOf { canvasSize.width / imageRectHeight }
    }
    val scaleY by remember(canvasSize.height, imageRectWidth) {
        derivedStateOf { canvasSize.height / imageRectWidth }
    }
    val scale by remember(scaleX, scaleY) { mutableFloatStateOf(scaleX.coerceAtLeast(scaleY)) }
    val offsetX by remember(scale) {
        mutableFloatStateOf((canvasSize.width - ceil(imageRectHeight * scale)) / 2.0f)
    }
    val offsetY by remember(scale) {
        mutableFloatStateOf((canvasSize.height - ceil(imageRectWidth * scale)) / 2.0f)
    }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                canvasSize = it.size.toSize()
            }
    ) {
        faces().faces.forEach {
            it?.let { face ->
                val rect = calculateRect(
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    boundingBox = face.boundingBox,
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.right, rect.bottom),
                    style = Stroke(borderSize)
                )
            }
        }
    }
}

private fun calculateRect(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    boundingBox: Rect,
): RectF = RectF().apply {
    left = boundingBox.left * scale + offsetX
    top = boundingBox.top * scale + offsetY
    right = boundingBox.width() * scale
    bottom = boundingBox.height() * scale
}

@Composable
private fun BackButton(
    onBackClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MyIconButton(
            iconId = R.drawable.baseline_arrow_back_24,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
private fun MyIconButton(
    @DrawableRes iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { onClick() },
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null
        )
    }
}

private val resolutionSelector = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(
            android.util.Size(480, 360),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
        )
    )
    .build()
