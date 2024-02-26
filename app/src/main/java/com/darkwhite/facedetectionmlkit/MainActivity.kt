package com.darkwhite.facedetectionmlkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.darkwhite.facedetectionmlkit.ui.theme.FaceDetectionMlKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FaceDetectionMlKitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent(requiredPermissions = REQUIRED_PERMISSIONS)
                }
            }
        }
    }
    
    // Note: The logic of handling the permissions is kept as if we need multiple permissions
    // (CAMERA & RECORD_AUDIO) if we want to record video while face detecting
    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
            ).toTypedArray()
    }
}
