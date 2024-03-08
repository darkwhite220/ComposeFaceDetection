package com.darkwhite.facedetectionmlkit

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.runtime.Immutable
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
import com.google.mlkit.vision.face.Face
import kotlin.math.ceil


@Composable
fun FaceDetectionScreenContent(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
) {
    var startFaceDetection by remember { mutableStateOf(true) }
    
    val density = LocalDensity.current
    val dpToPx = remember { with(density) { 1.dp.toPx() } }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    
    var faces by remember { mutableStateOf(FacesData(emptyList())) }
    var imageRect by remember { mutableStateOf(Rect()) }
    
    val onFaceDetected = { facesList: List<Face?>, rect: Rect ->
        faces = FacesData(
            if (startFaceDetection) {
                facesList
            } else {
                emptyList()
            }
        )
        imageRect = rect
    }
    
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
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
    }
    
    val previewView = remember {
        PreviewView(context)
            .apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
    }
    val preview = remember {
        Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
    }
    
    val cameraSelector = remember {
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    }
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    FaceDetectionUiContent(
        modifier = modifier,
        previewView = previewView,
        faces = { faces },
        imageRect = { imageRect },
        dpToPx = dpToPx,
    )
    
    // Back button & Pause/Resume detection button
    UiComponents(
        startFaceDetection = { startFaceDetection },
        onFaceDetectClick = { startFaceDetection = !startFaceDetection },
        onBackClick = onBackClick,
    )
    
    LaunchedEffect(key1 = startFaceDetection) {
        // Resume face detection
        if (startFaceDetection) {
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                }, executor
            )
            // Pause face detection
        } else {
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbind(imageAnalysis)
                }, executor
            )
        }
    }
}

@Composable
private fun FaceDetectionUiContent(
    modifier: Modifier = Modifier,
    previewView: PreviewView,
    faces: () -> FacesData,
    imageRect: () -> Rect,
    dpToPx: Float,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { previewView },
    )
    
    val borderSize by remember { mutableFloatStateOf(dpToPx * 2) }
    
    // Option 1: One time calculation based of the size of the canvas
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
        // Option 2: Get canvas size from the DrawScope, but repeat the calculations every time
        // faces change
//        val imageRectWidth = imageRect().width().toFloat()
//        val imageRectHeight = imageRect().height().toFloat()
//        val scaleX = size.width / imageRectHeight
//        val scaleY = size.height / imageRectWidth
//        val scale = scaleX.coerceAtLeast(scaleY)
//
//        // Calculate offset (we need to center the overlay on the target)
//        val offsetX = (size.width - ceil(imageRectHeight * scale)) / 2.0f
//        val offsetY = (size.height - ceil(imageRectWidth * scale)) / 2.0f
        
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
private fun UiComponents(
    startFaceDetection: () -> Boolean,
    onFaceDetectClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MyIconButton(
            iconId = R.drawable.baseline_arrow_back_24,
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.TopStart)
        )
        
        Button(
            onClick = onFaceDetectClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text(text = if (startFaceDetection()) "Pause Face Detection" else "Start Face Detection")
        }
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

@Immutable
data class FacesData(val faces: List<Face?>)

private val resolutionSelector = ResolutionSelector.Builder()
    .setResolutionStrategy(
        ResolutionStrategy(
            android.util.Size(480, 360),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
        )
    )
    .build()
