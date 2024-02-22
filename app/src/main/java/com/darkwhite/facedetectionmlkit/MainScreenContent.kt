package com.darkwhite.facedetectionmlkit

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var startFaceDetection by remember { mutableStateOf(false) }
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
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showPermissionNeeded) {
            Button(onClick = { initRequestPermission = true }) {
                Text(text = "Request Permissions")
            }
        } else {
            Button(onClick = { startFaceDetection = true }) {
                Text(text = "Start Face Detection")
            }
        }
    }
    
    if (initRequestPermission) {
        requestPermission.launch(requiredPermissions)
        initRequestPermission = false
    }
    
    if (startFaceDetection) {
        FaceDetectionScreenContent(
            onBackClick = { startFaceDetection = false }
        )
    }
}
