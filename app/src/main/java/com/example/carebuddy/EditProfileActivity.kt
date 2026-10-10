package com.example.carebuddy

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditProfileActivity : AppCompatActivity() {

    private var isCompanion = false
    private lateinit var uid: String

    // ===== VIEWS =====
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etBio: EditText
    private lateinit var etSkills: EditText
    private lateinit var etHourlyRate: EditText
    private lateinit var spGender: Spinner
    private lateinit var tvAvatarInitial: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var companionSection: LinearLayout

    // ===== PHOTO STATE =====
    private var pendingPhotoBase64: String? = null
    private var companionVerificationStatus = "pending"
    private var companionAvailability = false
    private var companionLocation = "Kuala Lumpur"
    private var companionAccountStatus = "Active"

    // ===== CAMERA PERMISSION =====
    private val CAMERA_PERMISSION = 502

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // ===== GET ROLE =====
        val role = intent.getStringExtra("role")
            ?: getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
                .getString("current_role", "elderly")
                .orEmpty()
        isCompanion = role.equals("companion", ignoreCase = true)

        // ===== CHECK AUTH =====
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        uid = currentUser.uid

        // ===== BIND VIEWS =====
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val avatarContainer = findViewById<FrameLayout>(R.id.avatarContainer)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)
        ivAvatar = findViewById(R.id.ivAvatar)
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etBio = findViewById(R.id.etBio)
        etSkills = findViewById(R.id.etSkills)
        etHourlyRate = findViewById(R.id.etHourlyRate)
        spGender = findViewById(R.id.spGender)
        val genderOptions = listOf("Prefer not to say", "Female", "Male")
        spGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        companionSection = findViewById(R.id.companionSection)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // ===== SHOW/HIDE COMPANION SECTION =====
        companionSection.visibility = if (isCompanion) View.VISIBLE else View.GONE

        // ===== CLOSE =====
        btnClose.setOnClickListener { finish() }

        // ===== AVATAR TAP → Photo Options =====
        avatarContainer.setOnClickListener { showPhotoOptions() }

        // ===== LOAD EXISTING DATA =====
        loadCurrentProfile()

        // ===== SAVE =====
        btnSave.setOnClickListener { saveProfile(btnSave) }
    }

    // ===================================================================
    // PHOTO OPTIONS DIALOG
    // ===================================================================
    private fun showPhotoOptions() {
        val options = arrayOf("Choose from gallery", "Take a photo", "Remove photo")

        AlertDialog.Builder(this)
            .setTitle("Profile photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> PhotoHelper.openGallery(this)
                    1 -> checkCameraAndCapture()
                    2 -> removePhoto()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===================================================================
    // CAMERA PERMISSION + CAPTURE
    // ===================================================================
    private fun checkCameraAndCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            PhotoHelper.openCamera(this)
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
                PhotoHelper.openCamera(this)
            } else {
                Toast.makeText(this, "Camera permission is needed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===================================================================
    // ACTIVITY RESULT HANDLER
    // ===================================================================
    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            PhotoHelper.REQUEST_PICK_IMAGE -> {
                val uri: Uri = data?.data ?: return
                val bitmap = PhotoHelper.bitmapFromUri(this, uri)
                if (bitmap != null) {
                    processNewPhoto(bitmap)
                } else {
                    Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show()
                }
            }
            PhotoHelper.REQUEST_CAPTURE_IMAGE -> {
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    processNewPhoto(bitmap)
                } else {
                    Toast.makeText(this, "Could not capture photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ===================================================================
    // PROCESS NEW PHOTO
    // ===================================================================
    private fun processNewPhoto(bitmap: Bitmap) {
        val base64 = PhotoHelper.bitmapToBase64(bitmap, maxSize = 400, maxBytes = 200_000)
        if (base64 == null) {
            Toast.makeText(this, "Photo is too large", Toast.LENGTH_SHORT).show()
            return
        }

        pendingPhotoBase64 = base64

        ivAvatar.setImageBitmap(bitmap)
        ivAvatar.visibility = View.VISIBLE
        tvAvatarInitial.visibility = View.GONE
    }

    // ===================================================================
    // REMOVE PHOTO
    // ===================================================================
    private fun removePhoto() {
        pendingPhotoBase64 = ""
        ivAvatar.setImageDrawable(null)
        ivAvatar.visibility = View.GONE
        tvAvatarInitial.visibility = View.VISIBLE

        val name = etName.text.toString().trim()
        tvAvatarInitial.text = name.firstOrNull()?.uppercase()?.toString() ?: "?"
    }

    // ===================================================================
    // LOAD EXISTING PROFILE
    // ===================================================================
    private fun loadCurrentProfile() {
        FirebaseCareBuddyStore.loadUserProfile(uid) { profile, error ->
            if (isFinishing || isDestroyed) return@loadUserProfile

            if (error != null || profile == null) {
                Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show()
                return@loadUserProfile
            }

            val name = (profile["displayName"] as? String).orEmpty()
            val phone = (profile["phone"] as? String).orEmpty()
            val photoBase64 = profile["photoBase64"] as? String

            etName.setText(name)
            etPhone.setText(phone)
            val gender = (profile["gender"] as? String).orEmpty()
            spGender.setSelection(listOf("Prefer not to say", "Female", "Male").indexOf(gender).coerceAtLeast(0))

            // ===== AVATAR =====
            if (!photoBase64.isNullOrBlank()) {
                val bitmap = PhotoHelper.base64ToBitmap(photoBase64)
                if (bitmap != null) {
                    ivAvatar.setImageBitmap(bitmap)
                    ivAvatar.visibility = View.VISIBLE
                    tvAvatarInitial.visibility = View.GONE
                } else {
                    showInitial(name)
                }
            } else {
                showInitial(name)
            }

            // ===== COMPANION FIELDS =====
            if (isCompanion) {
                FirebaseCareBuddyStore.loadCompanionProfile(uid) { cp, cpError ->
                    if (isFinishing || isDestroyed) return@loadCompanionProfile

                    if (cpError == null && cp != null) {
                        val bio = (cp["bio"] as? String).orEmpty()
                        val rate = (cp["hourlyRate"] as? String).orEmpty()
                        val skillsList = (cp["skills"] as? List<*>)?.filterIsInstance<String>().orEmpty()

                        etBio.setText(bio)
                        etSkills.setText(skillsList.joinToString(", "))
                        etHourlyRate.setText(rate)
                        companionVerificationStatus = cp["verificationStatus"] as? String ?: "pending"
                        companionAvailability = cp["available"] as? Boolean ?: false
                        companionLocation = cp["location"] as? String ?: "Kuala Lumpur"
                        companionAccountStatus = cp["accountStatus"] as? String ?: "Active"
                    }
                }
            }
        }
    }

    private fun showInitial(name: String) {
        ivAvatar.visibility = View.GONE
        tvAvatarInitial.visibility = View.VISIBLE
        tvAvatarInitial.text = name.trim().firstOrNull()?.uppercase()?.toString() ?: "?"
    }

    // ===================================================================
    // SAVE PROFILE
    // ===================================================================
    private fun saveProfile(btnSave: Button) {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        // ===== VALIDATION =====
        when {
            name.isBlank() -> {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return
            }
            name.length < 2 -> {
                Toast.makeText(this, "Name is too short", Toast.LENGTH_SHORT).show()
                return
            }
            phone.isBlank() -> {
                Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                return
            }
            phone.length < 7 -> {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val bio = etBio.text.toString().trim()
        val skillsText = etSkills.text.toString().trim()
        val hourlyRate = etHourlyRate.text.toString().trim()

        if (isCompanion) {
            when {
                bio.isBlank() -> {
                    Toast.makeText(this, "Please add a short bio", Toast.LENGTH_SHORT).show()
                    return
                }
                bio.length < 10 -> {
                    Toast.makeText(this, "Bio must be at least 10 characters", Toast.LENGTH_SHORT).show()
                    return
                }
                skillsText.isBlank() -> {
                    Toast.makeText(this, "Please add at least one skill", Toast.LENGTH_SHORT).show()
                    return
                }
                hourlyRate.isBlank() -> {
                    Toast.makeText(this, "Please set an hourly rate", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val db = FirebaseFirestore.getInstance()
        val mergeOptions = SetOptions.merge()

        // ===== BUILD USER UPDATES =====
        val selectedGender = spGender.selectedItem.toString()
        val userUpdates = mutableMapOf<String, Any>(
            "displayName" to name,
            "phone" to phone,
            "gender" to selectedGender
        )

        pendingPhotoBase64?.let { b64 ->
            userUpdates["photoBase64"] = b64
        }

        // ===== SAVE TO users/{uid} =====
        db.collection("users").document(uid)
            .set(userUpdates, mergeOptions)
            .addOnSuccessListener {
                if (isCompanion) {
                    val skillsArray = skillsText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val companionUpdates = mapOf<String, Any>(
                        "uid" to uid,
                        "displayName" to name,
                        "location" to companionLocation,
                        "accountStatus" to companionAccountStatus,
                        "bio" to bio,
                        "skills" to skillsArray,
                        "hourlyRate" to hourlyRate,
                        "gender" to selectedGender,
                        "verificationStatus" to companionVerificationStatus,
                        "available" to companionAvailability,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    // ===== SAVE TO companionProfiles/{uid} =====
                    db.collection("companionProfiles").document(uid)
                        .set(companionUpdates, mergeOptions)
                        .addOnSuccessListener { onSaveSuccess(name, btnSave) }
                        .addOnFailureListener { e -> onSaveError(e, btnSave) }
                } else {
                    onSaveSuccess(name, btnSave)
                }
            }
            .addOnFailureListener { e -> onSaveError(e, btnSave) }
    }

    // ===================================================================
    // SAVE SUCCESS
    // ===================================================================
    private fun onSaveSuccess(name: String, btnSave: Button) {
        val prefs = getSharedPreferences("carebuddy_demo", MODE_PRIVATE)
        val email = prefs.getString("current_email", "").orEmpty()

        prefs.edit()
            .putString("current_name", name)
            .putString("profile_name:$email", name)
            .apply {
                if (isCompanion) putString("companion_name", name)
            }
            .apply()

        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
        btnSave.isEnabled = true
        btnSave.text = "Save Changes"

        setResult(RESULT_OK)
        finish()
    }

    // ===================================================================
    // SAVE ERROR
    // ===================================================================
    private fun onSaveError(e: Exception, btnSave: Button) {
        Toast.makeText(
            this,
            "Failed to save: ${e.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
        btnSave.isEnabled = true
        btnSave.text = "Save Changes"
    }
}
