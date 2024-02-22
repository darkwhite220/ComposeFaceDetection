package com.darkwhite.facedetectionmlkit

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat


@Preview
@Composable
fun MainScreenContent(
    modifier: Modifier = Modifier,
    requiredPermissions: Array<String> = emptyArray()
) {
    var showPermissionNeeded by remember { mutableStateOf(true) }
    var initRequestPermission by remember { mutableStateOf(false) }
    var startFaceDetection by rememberSaveable { mutableStateOf(FaceDetectionFeature.OFF) }
    val requestPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        println("requestPermission result: $result")
        result.entries.filter { entry ->
            !entry.value
        }.let { notPermittedList ->
            showPermissionNeeded = notPermittedList.isNotEmpty()
        }
    }
    
    requiredPermissions.filter { permission ->
        ActivityCompat.checkSelfPermission(
            LocalContext.current,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }.let { notPermittedList ->
        showPermissionNeeded = notPermittedList.isNotEmpty()
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showPermissionNeeded) {
            Button(onClick = { initRequestPermission = true }) {
                Text(text = "Request Permissions")
            }
        } else {
            MyButton(
                text = "Continues Face Detection",
                onClick = { startFaceDetection = FaceDetectionFeature.CONTINUES },
            )
            MyButton(
                text = "Face Detection w/ Pause/Resume",
                onClick = { startFaceDetection = FaceDetectionFeature.PAUSE_RESUME },
            )
            MyButton(
                text = "Front Camera Face Detection",
                onClick = { startFaceDetection = FaceDetectionFeature.FRONT_CAMERA },
            )
            MyButton(
                text = "Screen & Face Rotation Face Detection",
                onClick = { startFaceDetection = FaceDetectionFeature.ROTATIONS },
            )
            MyButton(
                text = "Take Photo while Face Detecting",
                onClick = { startFaceDetection = FaceDetectionFeature.TAKE_PHOTO },
            )
        }
    }
    
    if (initRequestPermission) {
        requestPermission.launch(requiredPermissions)
        initRequestPermission = false
    }
    
    when (startFaceDetection) {
        FaceDetectionFeature.CONTINUES -> FaceDetectionContinuesScreenContent(
            onBackClick = { startFaceDetection = FaceDetectionFeature.OFF }
        )
        FaceDetectionFeature.PAUSE_RESUME -> FaceDetectionScreenContent(
            onBackClick = { startFaceDetection = FaceDetectionFeature.OFF }
        )
        FaceDetectionFeature.FRONT_CAMERA -> FaceDetectionFrontCameraScreenContent(
            onBackClick = { startFaceDetection = FaceDetectionFeature.OFF }
        )
        FaceDetectionFeature.ROTATIONS -> FaceDetectionRotationScreenContent(
            onBackClick = { startFaceDetection = FaceDetectionFeature.OFF }
        )
        FaceDetectionFeature.TAKE_PHOTO -> FaceDetectionTakePhotoScreenContent(
            onBackClick = { startFaceDetection = FaceDetectionFeature.OFF }
        )
        FaceDetectionFeature.OFF -> {}
    }
}

enum class FaceDetectionFeature {
    OFF, PAUSE_RESUME, CONTINUES, FRONT_CAMERA, ROTATIONS, TAKE_PHOTO
}

@Composable
private fun MyButton(
    text: String,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(text = text)
    }
}