package com.mantracounter.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
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
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            applyDeityImage(it)
            prefs.edit().putString("image_uri", it.toString()).apply()
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickImage.launch("image/*")
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("mantra_prefs", Context.MODE_PRIVATE)

        // Restore saved state
        count = prefs.getInt("count", 0)
        updateCountDisplay(animate = false)

        // Restore deity image
        prefs.getString("image_uri", null)?.let { uriStr ->
            try {
                applyDeityImage(Uri.parse(uriStr))
            } catch (_: Exception) {}
        }

        // Start pulse animation on ring
        startPulseAnimation()

        // === CLICK HANDLERS ===

        binding.btnCount.setOnClickListener {
            count++
            updateCountDisplay(animate = true)
            prefs.edit().putInt("count", count).apply()
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animateButton(it)

            // Celebrate mala completion
            if (count % 108 == 0 && count > 0) {
                Toast.makeText(this, getString(R.string.mala_complete), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCount.setOnLongClickListener {
            showResetDialog()
            true
        }

        binding.btnReset.setOnClickListener {
            showResetDialog()
        }

        binding.btnMinusOne.setOnClickListener {
            if (count > 0) {
                count--
                updateCountDisplay(animate = false)
                prefs.edit().putInt("count", count).apply()
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        binding.btnChangePhoto.setOnClickListener {
            requestImagePermissionAndPick()
        }

        // Tapping the card also opens picker
        binding.cardImage.setOnClickListener {
            requestImagePermissionAndPick()
        }

        binding.btnClearImage.setOnClickListener {
            binding.ivGoddess.setImageDrawable(null)
            binding.ivGoddess.visibility = View.GONE
            binding.layoutUploadHint.visibility = View.VISIBLE
            prefs.edit().remove("image_uri").apply()
        }
    }

    private fun applyDeityImage(uri: Uri) {
        binding.ivGoddess.setImageURI(uri)
        binding.ivGoddess.visibility = View.VISIBLE
        binding.layoutUploadHint.visibility = View.GONE
    }

    private fun requestImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*")
        } else {
            requestPermission.launch(permission)
        }
    }

    private fun updateCountDisplay(animate: Boolean) {
        val malasDone = count / 108
        val beadsInCurrentMala = count % 108

        binding.tvCount.text = count.toString()
        binding.tvTotalCount.text = count.toString()
        binding.tvMalaCount.text = malasDone.toString()
        binding.tvMalaProgress.text = "$beadsInCurrentMala / 108"
        binding.malaProgressView.progress = beadsInCurrentMala

        if (animate) {
            val anim = AnimationUtils.loadAnimation(this, R.anim.count_bounce)
            binding.tvCount.startAnimation(anim)
        }
    }

    private fun animateButton(view: View) {
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.90f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.90f)
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.90f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.90f, 1f)

        scaleDownX.duration = 60
        scaleDownY.duration = 60
        scaleUpX.duration = 150
        scaleUpY.duration = 150
        scaleUpX.interpolator = OvershootInterpolator(2f)
        scaleUpY.interpolator = OvershootInterpolator(2f)

        AnimatorSet().apply {
            play(scaleDownX).with(scaleDownY)
            play(scaleUpX).with(scaleUpY).after(scaleDownX)
            start()
        }
    }

    private fun startPulseAnimation() {
        val ring = binding.viewPulseRing

        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.12f, 1f)
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.12f, 1f)
        val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.5f, 0.15f, 0.5f)

        scaleX.duration = 1800
        scaleY.duration = 1800
        alpha.duration = 1800

        AnimatorSet().apply {
            play(scaleX).with(scaleY).with(alpha)
            start()
        }

        // Repeat
        ring.postDelayed({ startPulseAnimation() }, 1800)
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this, R.style.Theme_MantraCounter_Dialog)
            .setTitle(getString(R.string.reset_title))
            .setMessage(getString(R.string.reset_message))
            .setPositiveButton(getString(R.string.reset_confirm)) { _, _ ->
                count = 0
                updateCountDisplay(animate = false)
                prefs.edit().putInt("count", 0).apply()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
