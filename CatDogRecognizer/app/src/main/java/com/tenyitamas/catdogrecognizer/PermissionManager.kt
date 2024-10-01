package com.tenyitamas.catdogrecognizer

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.PickVisualMediaRequest

@Composable
fun RequestPermissionsAndPickImage(onImagePicked: (Uri?) -> Unit) {
    val context = LocalContext.current

    // Launcher for picking images
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImagePicked(uri)
    }

    // Launcher for requesting permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Create a request for images only
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            pickImageLauncher.launch(request)
        } else {
            // Handle permission denial if necessary
        }
    }

    Button(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }) {
        Text("Pick Image")
    }
}
