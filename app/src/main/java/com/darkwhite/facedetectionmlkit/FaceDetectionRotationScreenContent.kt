package com.darkwhite.facedetectionmlkit

import android.content.res.Configuration
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
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
fun FaceDetectionRotationScreenContent(
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
        onFaceDetected = onFaceDetected
    )
    
    BackButton(onBackClick = onBackClick)
    
}

@Composable
private fun FaceDetectionUiContent(
    modifier: Modifier = Modifier,
    faces: () -> FacesData,
    imageRect: () -> Rect,
    dpToPx: Float,
    onFaceDetected: (List<Face?>, Rect) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val executor = ContextCompat.getMainExecutor(context)
            
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
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                    )
                }, executor
            )
            
            previewView
        },
    )
    
    DrawBoxes(
        dpToPx = dpToPx,
        imageRect = imageRect,
        faces = faces
    )
}

@Composable
private fun DrawBoxes(
    dpToPx: Float,
    imageRect: () -> Rect,
    faces: () -> FacesData
) {
    val borderSize by remember { mutableFloatStateOf(dpToPx * 2) }
    val configuration = LocalConfiguration.current
    val isLandScape by remember(configuration.orientation) {
        derivedStateOf { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }
    
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val imageRectWidth by remember(imageRect(), isLandScape) {
        derivedStateOf {
            whenLandScapeWidth(
                isLandScape = isLandScape,
                width = imageRect().width().toFloat(),
                height = imageRect().height().toFloat()
            )
        }
    }
    val imageRectHeight by remember(imageRect(), isLandScape) {
        derivedStateOf {
            whenLandScapeHeight(
                isLandScape = isLandScape,
                width = imageRect().width().toFloat(),
                height = imageRect().height().toFloat()
            )
        }
    }
    val scaleX by remember(canvasSize.width, imageRectWidth) {
        derivedStateOf { canvasSize.width / imageRectWidth }
    }
    val scaleY by remember(canvasSize.height, imageRectHeight) {
        derivedStateOf { canvasSize.height / imageRectHeight }
    }
    val scale by remember(scaleX, scaleY) { mutableFloatStateOf(scaleX.coerceAtLeast(scaleY)) }
    val offsetX by remember(scale) {
        mutableFloatStateOf((canvasSize.width - ceil(imageRectWidth * scale)) / 2.0f)
    }
    val offsetY by remember(scale) {
        mutableFloatStateOf((canvasSize.height - ceil(imageRectHeight * scale)) / 2.0f)
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
                rotate(
                    degrees = -face.headEulerAngleZ,
                    pivot = Offset(rect.left + (rect.right / 2), rect.top + (rect.bottom / 2))
                ) {
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
}

private fun whenLandScapeWidth(isLandScape: Boolean, width: Float, height: Float): Float {
    return when (isLandScape) {
        true -> width
        false -> height
    }
}

private fun whenLandScapeHeight(isLandScape: Boolean, width: Float, height: Float): Float {
    return when (isLandScape) {
        true -> height
        false -> width
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
