package com.mantracounter.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.TimePickerDialog
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var count = 0

    // ---- Image picker ----
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { /* URI may not support persistable permissions */ }
            applyDeityImage(it)
            prefs.edit().putString("image_uri", it.toString()).apply()
        }
    }

    private val requestStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickImage.launch("image/*")
        else Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_SHORT).show()
    }

    // ---- Notification permission (API 33+) ----
    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showTimePickerForReminder()
        else Toast.makeText(this, getString(R.string.notif_permission_needed), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("mantra_prefs", Context.MODE_PRIVATE)
        ReminderReceiver.ensureNotificationChannel(this)

        // Restore count and streak
        count = prefs.getInt("count", 0)
        updateCountDisplay(animate = false)
        binding.tvStreakDays.text = prefs.getInt("streak_days", 0).toString()

        // Restore deity image
        prefs.getString("image_uri", null)?.let { uriStr ->
            try { applyDeityImage(Uri.parse(uriStr)) } catch (e: Exception) { /* stale URI */ }
        }

        startPulseAnimation()

        // ===== CLICK HANDLERS =====

        binding.btnCount.setOnClickListener {
            count++
            incrementStreakIfNeeded()
            updateCountDisplay(animate = true)
            prefs.edit().putInt("count", count).apply()
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animateButton(it)
            if (count % 108 == 0) {
                Toast.makeText(this, getString(R.string.mala_complete), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCount.setOnLongClickListener {
            showResetDialog()
            true
        }

        binding.btnReset.setOnClickListener { showResetDialog() }

        binding.btnMinusOne.setOnClickListener {
            if (count > 0) {
                count--
                updateCountDisplay(animate = false)
                prefs.edit().putInt("count", count).apply()
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        binding.btnChangePhoto.setOnClickListener { requestImagePermissionAndPick() }
        binding.cardImage.setOnClickListener { requestImagePermissionAndPick() }

        binding.btnClearImage.setOnClickListener {
            binding.ivGoddess.setImageDrawable(null)
            binding.ivGoddess.visibility = View.GONE
            binding.layoutUploadHint.visibility = View.VISIBLE
            prefs.edit().remove("image_uri").apply()
        }

        binding.btnReminder.setOnClickListener { onReminderClicked() }
    }

    // ===== STREAK =====

    private fun incrementStreakIfNeeded() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val lastChantDate = prefs.getString("last_chant_date", null)
        var streak = prefs.getInt("streak_days", 0)

        // Already counted today — no change
        if (lastChantDate == today) return

        streak = when {
            lastChantDate == null -> 1
            else -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                if (lastChantDate == sdf.format(yesterday.time)) streak + 1 else 1
            }
        }

        prefs.edit()
            .putString("last_chant_date", today)
            .putInt("streak_days", streak)
            .apply()

        binding.tvStreakDays.text = streak.toString()
    }

    // ===== REMINDER =====

    private fun onReminderClicked() {
        val hasReminder = prefs.getInt("reminder_hour", -1) != -1
        if (hasReminder) {
            showReminderOptionsDialog()
        } else {
            requestNotifAndShowPicker()
        }
    }

    private fun showReminderOptionsDialog() {
        val hour = prefs.getInt("reminder_hour", 7)
        val minute = prefs.getInt("reminder_minute", 0)
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

        AlertDialog.Builder(this, R.style.Theme_MantraCounter_Dialog)
            .setTitle(getString(R.string.set_reminder))
            .setMessage(getString(R.string.reminder_set, timeStr))
            .setPositiveButton(getString(R.string.change_photo).replace("Photo", "Time")) { _, _ ->
                requestNotifAndShowPicker()
            }
            .setNeutralButton(getString(R.string.cancel_reminder)) { _, _ ->
                cancelReminder()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun requestNotifAndShowPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                showTimePickerForReminder()
            } else {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            showTimePickerForReminder()
        }
    }

    private fun showTimePickerForReminder() {
        val hour = prefs.getInt("reminder_hour", 7)
        val minute = prefs.getInt("reminder_minute", 0)

        TimePickerDialog(this, { _, h, m ->
            scheduleReminder(h, m)
        }, hour, minute, false).also {
            it.setTitle(getString(R.string.reminder_dialog_title))
            it.show()
        }
    }

    private fun scheduleReminder(hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        prefs.edit()
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        ReminderReceiver.scheduleAlarm(this, cal.timeInMillis)

        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        Toast.makeText(this, getString(R.string.reminder_set, timeStr), Toast.LENGTH_SHORT).show()
        binding.btnReminder.alpha = 1.0f  // highlight bell as active
    }

    private fun cancelReminder() {
        ReminderReceiver.cancelAlarm(this)
        prefs.edit()
            .remove("reminder_hour")
            .remove("reminder_minute")
            .apply()
        Toast.makeText(this, getString(R.string.reminder_cancelled), Toast.LENGTH_SHORT).show()
        binding.btnReminder.alpha = 0.8f
    }

    // ===== UI =====

    private fun applyDeityImage(uri: Uri) {
        binding.ivGoddess.setImageURI(uri)
        binding.ivGoddess.visibility = View.VISIBLE
        binding.layoutUploadHint.visibility = View.GONE
    }

    private fun requestImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            pickImage.launch("image/*")
        else
            requestStoragePermission.launch(permission)
    }

    private fun updateCountDisplay(animate: Boolean) {
        val malasDone = count / 108
        val beadsInMala = count % 108

        binding.tvCount.text = count.toString()
        binding.tvTotalCount.text = count.toString()
        binding.tvMalaCount.text = malasDone.toString()
        binding.tvMalaProgress.text = "$beadsInMala / 108"
        binding.malaProgressView.progress = beadsInMala

        if (animate) {
            binding.tvCount.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.count_bounce)
            )
        }
    }

    private fun animateButton(view: View) {
        val downX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.90f).apply { duration = 60 }
        val downY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.90f).apply { duration = 60 }
        val upX = ObjectAnimator.ofFloat(view, "scaleX", 0.90f, 1f).apply {
            duration = 150; interpolator = OvershootInterpolator(2f)
        }
        val upY = ObjectAnimator.ofFloat(view, "scaleY", 0.90f, 1f).apply {
            duration = 150; interpolator = OvershootInterpolator(2f)
        }
        AnimatorSet().apply {
            play(downX).with(downY)
            play(upX).with(upY).after(downX)
            start()
        }
    }

    private fun startPulseAnimation() {
        val ring = binding.viewPulseRing
        // Use infinite ValueAnimator — no memory leak from postDelayed loops
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val scale = 1f + 0.12f * Math.sin(fraction * Math.PI).toFloat()
                ring.scaleX = scale
                ring.scaleY = scale
                ring.alpha = 0.5f - 0.35f * fraction
            }
            start()
        }
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
