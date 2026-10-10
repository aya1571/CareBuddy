package com.example.carebuddy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream

class IcVerificationActivity : AppCompatActivity() {

    // ===== VIEWS =====
    private lateinit var icImage: ImageView
    private lateinit var faceImage: ImageView
    private lateinit var tvFaceStatus: TextView
    private lateinit var btnUploadIc: Button
    private lateinit var btnScanFace: Button
    private lateinit var btnVerifyIc: Button
    private lateinit var btnContinueLocal: Button
    private lateinit var cbConsent: CheckBox

    // ===== STATE =====
    private var selectedImageUri: Uri? = null
    private var facePhoto: Bitmap? = null

    // ===== REQUEST CODES =====
    private val PICK_IMAGE = 100
    private val TAKE_FACE_PHOTO = 101
    private val CAMERA_PERMISSION = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ic_verification)

        // ===== BIND VIEWS =====
        icImage = findViewById(R.id.ivIcImage)
        faceImage = findViewById(R.id.ivFaceImage)
        tvFaceStatus = findViewById(R.id.tvFaceStatus)
        btnUploadIc = findViewById(R.id.btnUploadIc)
        btnScanFace = findViewById(R.id.btnScanFace)
        btnVerifyIc = findViewById(R.id.btnVerifyIc)
        btnContinueLocal = findViewById(R.id.btnContinueLocal)
        cbConsent = findViewById(R.id.cbVerificationConsent)

        // ===== UPLOAD IC =====
        btnUploadIc.setOnClickListener { openImagePicker() }

        // ===== SCAN FACE =====
        btnScanFace.setOnClickListener { captureFacePhoto() }

        // ===== VERIFY (ONLINE) =====
        btnVerifyIc.setOnClickListener {
            if (selectedImageUri == null || facePhoto == null) {
                Toast.makeText(this, "Please add both your IC and a face photo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!cbConsent.isChecked) {
                Toast.makeText(this, "Please agree before sending identity images for review", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            submitVerification()
        }

        // ===== CONTINUE (LOCAL FALLBACK) =====
        btnContinueLocal.setOnClickListener {
            if (selectedImageUri == null || facePhoto == null) {
                Toast.makeText(this, "Please add both your IC and a face photo first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val role = intent.getStringExtra(RolePortalActivity.EXTRA_ROLE) ?: "elderly"
            completeVerification(
                role = role,
                name = intent.getStringExtra("name"),
                email = intent.getStringExtra("email")?.trim()?.lowercase(),
                uploadedOnline = false
            )
        }
    }

    // ===================================================================
    // IC PICKER
    // ===================================================================
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_IMAGE)
    }

    // ===================================================================
    // FACE CAPTURE
    // ===================================================================
    private fun captureFacePhoto() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startActivityForResult(
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
                TAKE_FACE_PHOTO
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(
                    Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
                    TAKE_FACE_PHOTO
                )
            } else {
                Toast.makeText(this, "Camera permission is needed to scan your face", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===================================================================
    // RESULT HANDLER
    // ===================================================================
    @Deprecated("Deprecated in Android API, but kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // --- IC PICKED ---
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            if (selectedImageUri != null) {
                icImage.setImageURI(selectedImageUri)
                icImage.background = null
                Toast.makeText(this, "IC image selected", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            }
        }

        // --- FACE CAPTURED ---
        if (requestCode == TAKE_FACE_PHOTO && resultCode == Activity.RESULT_OK) {
            facePhoto = data?.extras?.get("data") as? Bitmap
            if (facePhoto != null) {
                faceImage.setImageBitmap(facePhoto)
                faceImage.background = null
                tvFaceStatus.text = "Face photo captured"
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===================================================================
    // SUBMIT — Base64 encode and save to Firestore
    // ===================================================================
    private fun submitVerification() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please sign in again before submitting verification.", Toast.LENGTH_LONG).show()
            return
        }

        val role = intent.getStringExtra(RolePortalActivity.EXTRA_ROLE) ?: "elderly"
        val name = intent.getStringExtra("name")
        val email = intent.getStringExtra("email")?.trim()?.lowercase()

        btnVerifyIc.isEnabled = false

        // ===== CONVERT IC IMAGE → BITMAP =====
        val icBitmap = uriToBitmap(selectedImageUri!!)
        if (icBitmap == null) {
            btnVerifyIc.isEnabled = true
            Toast.makeText(this, "Could not read IC image. Try another.", Toast.LENGTH_LONG).show()
            return
        }

        // ===== COMPRESS & ENCODE IC =====
        val icBase64 = compressToBase64(icBitmap, maxSize = 700, maxBytes = 350_000)

        // ===== COMPRESS & ENCODE FACE =====
        val faceBitmap = facePhoto!!
        val faceBase64 = compressToBase64(faceBitmap, maxSize = 600, maxBytes = 120_000)

        if (icBase64 == null || faceBase64 == null) {
            btnVerifyIc.isEnabled = true
            Toast.makeText(
                this,
                "Images are too large. Try a smaller IC photo.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // ===== SAVE TO FIRESTORE =====
        FirebaseCareBuddyStore.submitVerificationBase64(
            uid = uid,
            role = role,
            icBase64 = icBase64,
            faceBase64 = faceBase64
        ) { error ->
            btnVerifyIc.isEnabled = true
            if (error != null) {
                btnContinueLocal.visibility = android.view.View.VISIBLE
                Toast.makeText(
                    this,
                    "Verification could not be saved: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                completeVerification(role, name, email, uploadedOnline = true)
            }
        }
    }

    // ===================================================================
    // Convert URI → Bitmap
    // ===================================================================
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ===================================================================
    // Compress bitmap and encode as Base64 with size guard
    // ===================================================================
    private fun compressToBase64(bitmap: Bitmap, maxSize: Int, maxBytes: Int): String? {
        return try {
            val scaled = scaleBitmap(bitmap, maxSize)
            val output = ByteArrayOutputStream()

            var quality = 75
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)

            // Reduce quality until it fits under maxBytes
            while (output.size() > maxBytes && quality > 20) {
                output.reset()
                quality -= 10
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }

            // Still too big? Scale down further
            if (output.size() > maxBytes) {
                val smaller = scaleBitmap(scaled, maxSize / 2)
                output.reset()
                quality = 60
                smaller.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }

            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ===================================================================
    // Scale bitmap so longest side ≤ maxSize
    // ===================================================================
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ===================================================================
    // COMPLETE VERIFICATION & NAVIGATE
    // ===================================================================
    private fun completeVerification(
        role: String,
        name: String?,
        email: String?,
        uploadedOnline: Boolean
    ) {
        Toast.makeText(
            this,
            if (uploadedOnline)
                "Verification submitted. An admin will review your images."
            else
                "Continuing without uploading photos. Identity review remains pending.",
            Toast.LENGTH_LONG
        ).show()

        getSharedPreferences("carebuddy_demo", MODE_PRIVATE).edit().apply {
            putString("current_role", role)
            putString(
                "identity_submission_mode",
                if (uploadedOnline) "uploaded" else "local_only"
            )
            putBoolean("ic_verification_pending", false)
            if (!email.isNullOrBlank()) {
                putString("current_email", email)
                putString("profile_role:$email", role)
                if (!name.isNullOrBlank()) putString("profile_name:$email", name)
            }
            if (!name.isNullOrBlank()) putString("current_name", name)
            if (role == "companion" && !name.isNullOrBlank()) {
                putString("companion_name", name)
            }
        }.apply()

        if (!email.isNullOrBlank() && !name.isNullOrBlank()) {
            LocalAccountStore.save(this, LocalAccount(email, name, role))
        }

        val homeIntent = if (role == "companion") {
            Intent(this, CompanionHomeActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        homeIntent.putExtra("name", name)

        startActivity(homeIntent)
        finish()
    }
}
