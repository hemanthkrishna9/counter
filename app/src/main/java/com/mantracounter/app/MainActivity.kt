package com.mantracounter.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mantracounter.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var count = 0

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Persist permission for the URI
            try {
                contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Some URIs don't support persistable permissions
            }
            binding.ivGoddess.setImageURI(it)
            binding.ivGoddess.visibility = View.VISIBLE
            binding.tvUploadHint.visibility = View.GONE
            prefs.edit().putString("image_uri", it.toString()).apply()
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickImage.launch("image/*")
        } else {
            Toast.makeText(this, "Permission needed to pick image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("mantra_prefs", Context.MODE_PRIVATE)

        // Restore saved count
        count = prefs.getInt("count", 0)
        updateCountDisplay()

        // Restore saved image
        val savedUri = prefs.getString("image_uri", null)
        if (savedUri != null) {
            try {
                binding.ivGoddess.setImageURI(Uri.parse(savedUri))
                binding.ivGoddess.visibility = View.VISIBLE
                binding.tvUploadHint.visibility = View.GONE
            } catch (e: Exception) {
                // Image no longer accessible
            }
        }

        // Tap counter button
        binding.btnCount.setOnClickListener {
            count++
            updateCountDisplay()
            prefs.edit().putInt("count", count).apply()
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // Long press to reset
        binding.btnCount.setOnLongClickListener {
            showResetDialog()
            true
        }

        // Reset button
        binding.btnReset.setOnClickListener {
            showResetDialog()
        }

        // Upload image
        binding.cardImage.setOnClickListener {
            requestImagePermissionAndPick()
        }

        // Clear image
        binding.btnClearImage.setOnClickListener {
            binding.ivGoddess.setImageDrawable(null)
            binding.ivGoddess.visibility = View.GONE
            binding.tvUploadHint.visibility = View.VISIBLE
            prefs.edit().remove("image_uri").apply()
        }
    }

    private fun requestImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                pickImage.launch("image/*")
            }
            else -> {
                requestPermission.launch(permission)
            }
        }
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Counter")
            .setMessage("Reset count to 0?")
            .setPositiveButton("Reset") { _, _ ->
                count = 0
                updateCountDisplay()
                prefs.edit().putInt("count", 0).apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCountDisplay() {
        binding.tvCount.text = count.toString()
        binding.tvMalas.text = "${count / 108} Malas  •  ${count % 108}/108"
    }
}
