package com.example.calorieapp

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class FoodScanActivity : AppCompatActivity() {

    private lateinit var takePictureButton: ImageButton
    private lateinit var uploadPictureButton: ImageButton
    private lateinit var hintEditText: TextInputEditText

    private lateinit var bottomNavView: BottomNavigationView

    private var currentPhotoUri: Uri? = null

    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent> // For Intent
    private lateinit var pickImageLauncher: ActivityResultLauncher<String> // For Gallery GetContent
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>> // For Gallery Permission

    private val GALLERY_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scan_food)

        takePictureButton = findViewById(R.id.take_picture_button)
        uploadPictureButton = findViewById(R.id.upload_picture_button)
        bottomNavView = findViewById(R.id.bottom_navigation)
        hintEditText = findViewById(R.id.hint_edit_text)

        setupLaunchers()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun setupLaunchers() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val galleryPermissionGranted = permissions.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false) ||
                    permissions.getOrDefault(Manifest.permission.READ_EXTERNAL_STORAGE, false)

            if (galleryPermissionGranted) {
                pickImageLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Gallery permission denied", Toast.LENGTH_SHORT).show()
            }
        }
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentPhotoUri?.let { uri ->
                    navigateToAnalysis(uri)
                }
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                navigateToAnalysis(it)
            }
        }
    }

    private fun setupClickListeners() {
        takePictureButton.setOnClickListener {
            launchCamera() // Directly launch, NO permission check here
        }

        uploadPictureButton.setOnClickListener {
            // Gallery button still needs permission check
            checkAndRequestPermission(GALLERY_PERMISSION)
        }
    }

    private fun checkAndRequestPermission(permissions: Array<String>) {
        // Check if the specific gallery permission is granted
        val permissionNeeded = permissions.firstOrNull {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionNeeded == null) {
            // Gallery permission already granted
            pickImageLauncher.launch("image/*")
        } else {
            // Request the gallery permission
            requestPermissionLauncher.launch(permissions)
        }
    }
    private fun createImageUri(): Uri? {
        return try {
            val imagePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile("temp_image_", ".jpg", imagePath)
            FileProvider.getUriForFile(
                applicationContext,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show()
            null
        }
    }
    private fun launchCamera() {
        val imageUri = createImageUri()
        if (imageUri == null) {
            Toast.makeText(this, "Error preparing camera", Toast.LENGTH_SHORT).show()
            return
        }
        currentPhotoUri = imageUri

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)

        // Add ClipData and Flags for robust permission granting
        cameraIntent.clipData = ClipData.newUri(contentResolver, "camera_photo", imageUri)
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        takePictureLauncher.launch(cameraIntent)
    }

    private fun navigateToAnalysis(uri: Uri) {
        val userHint = hintEditText.text.toString().trim()

        val intent = Intent(this, FoodAnalysisActivity::class.java).apply {
            putExtra(FoodAnalysisActivity.EXTRA_IMAGE_URI, uri.toString())
            putExtra(FoodAnalysisActivity.EXTRA_USER_HINT, userHint)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        startActivity(intent, options.toBundle())
    }

    private fun setupBottomNavigation() {
        bottomNavView.selectedItemId = R.id.nav_camera
        bottomNavView.setOnItemSelectedListener { item ->
            val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent, options.toBundle())
                    true
                }
                R.id.nav_camera -> true
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent, options.toBundle())
                    true
                }
                else -> false
            }
        }
        bottomNavView.setOnItemReselectedListener { /* Do nothing */ }
    }

    override fun onResume() {
        super.onResume()
        bottomNavView.selectedItemId = R.id.nav_camera
        hintEditText.text = null
    }

}