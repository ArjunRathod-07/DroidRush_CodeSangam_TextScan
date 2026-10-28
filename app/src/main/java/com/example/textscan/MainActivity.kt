package com.example.textscan

import android.Manifest








import android.app.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_CODE = 100
    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var imageView: ImageView
    private lateinit var btnCapture: View
    private lateinit var btnGallery: View
    private lateinit var tvExtractedText: TextView

    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private fun showResultPopup(scannedText: String) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_result)

        val tvResult = dialog.findViewById<TextView>(R.id.tvScannedResult)
        val btnClose = dialog.findViewById<Button?>(R.id.btnCloseDialog)
        val btnHeaderClose = dialog.findViewById<Button?>(R.id.btnHeaderClose)

        tvResult.text = if (scannedText.isNotBlank()) scannedText else "No text found in this image."

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }
        btnHeaderClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI Views
        viewFinder = findViewById(R.id.viewFinder)
        imageView = findViewById(R.id.imageView)
        btnCapture = findViewById(R.id.btnCapture)
        btnGallery = findViewById(R.id.btnGallery)
        tvExtractedText = findViewById(R.id.tvExtractedText)
        tvExtractedText.movementMethod = ScrollingMovementMethod()

        // Initialize the Gallery Launcher
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                viewFinder.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                imageView.setImageURI(it)
                processImageWithMLKit(it)
            }
        }

        // Check and request camera permission on launch
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            startCamera()
        }

        // Set Button Click Listeners
        btnCapture.setOnClickListener {
            if (viewFinder.visibility == View.VISIBLE && imageCapture != null) {
                takePhoto()
            } else {
                // If viewing image, switch back to camera preview
                imageView.visibility = View.GONE
                viewFinder.visibility = View.VISIBLE
            }
        }

        btnGallery.setOnClickListener {
            openGallery()
        }
    }

    private fun openGallery() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open gallery: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val outputDirectory = externalMediaDirs.firstOrNull() ?: filesDir
        val photoFile = File(outputDirectory, "textscan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    viewFinder.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageView.setImageURI(savedUri)
                    processImageWithMLKit(savedUri)
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required for live scan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageWithMLKit(imageUri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    if (text.isNotEmpty()) {
                        tvExtractedText.text = text
                    } else {
                        tvExtractedText.text = "No text found in image."
                    }
                    showResultPopup(text)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "OCR Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
