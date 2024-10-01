package com.tenyitamas.catdogrecognizer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tenyitamas.catdogrecognizer.ui.theme.CatDogRecognizerTheme
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.exp
import kotlin.math.roundToInt


fun loadPytorchModel(context: Context): Module {
    Log.d("ModelLoad", "Loading model from assets")
    val modelPath = assetFilePath(context, "cat_dog_lite_traced.ptl")
    Log.d("ModelLoad", "Model path: $modelPath")
    return Module.load(modelPath)
}

fun assetFilePath(context: Context, assetName: String): String {
    val file = File(context.filesDir, assetName)
    try {
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
    } catch (e: IOException) {
        throw RuntimeException("Error processing assets", e)
    }
    return file.absolutePath
}

fun preprocessImagePytorch(bitmap: Bitmap, inputSize: Int): Tensor {
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
    val tensor = TensorImageUtils.bitmapToFloat32Tensor(
        resizedBitmap,
        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, // Normalization mean
        TensorImageUtils.TORCHVISION_NORM_STD_RGB   // Normalization std
    )
    Log.d("NormalizationValues", "Mean: ${TensorImageUtils.TORCHVISION_NORM_MEAN_RGB.joinToString()}")
    Log.d("NormalizationValues", "Std: ${TensorImageUtils.TORCHVISION_NORM_STD_RGB.joinToString()}")
    return tensor
}

fun runPytorchInference(module: Module, inputTensor: Tensor): FloatArray {
    val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
    return outputTensor.dataAsFloatArray // Return output tensor as float array
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatDogRecognizerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    ImagePickerScreen(paddingValues)
                }
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
@Composable
fun ImagePickerScreen(
    paddingValues: PaddingValues
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var predictionResult by remember { mutableStateOf<FloatArray?>(null) }

    val context = LocalContext.current

    // Load PyTorch model
    Log.d("PytorchLoad", "Attempting to load Pytorch JNI")
    val pytorchModule = remember { loadPytorchModel(context) }
    Log.d("PytorchLoad", "Pytorch JNI loaded successfully")

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        selectedImageUri = uri

        uri?.let {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)

                // Preprocess image for PyTorch
                val inputTensor = preprocessImagePytorch(bitmap, inputSize = 224)

                // Run inference and get result
                predictionResult = runPytorchInference(pytorchModule, inputTensor)
            } catch (e: Exception) {
                Log.e("ImagePickerScreen", "Error processing image or running inference", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        predictionResult?.let { result ->
            val predictionPercentage = (exp(result[1])/(exp(result[0]) + exp(result[1])) * 100).roundToInt()
            val text = when(predictionPercentage) {
                in 66..100 -> "A kép egy kutya ($predictionPercentage%)"
                in 0..33 -> "A kép egy macska (${100 - predictionPercentage}%)"
                else -> "Egyik sem ($predictionPercentage%)"
            }
            Text(
                text = text,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            Log.d("Prediction", "Raw result: ${result.joinToString()}")
        }

        selectedImageUri?.let { uri ->
            val bitmap = remember(uri) {
                val contentResolver = context.contentResolver
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            Text(
                text = "Nincs kiválasztott kép",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }

        Button(
            onClick = {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .padding(top = 16.dp)
        ) {
            Text("Válassz ki egy képet")
        }
    }
}