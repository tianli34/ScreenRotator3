package com.example.screenrotator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.example.screenrotator.databinding.ActivityMainBinding

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrant.setOnClickListener { openOverlaySettings() }
        binding.btnAuto.setOnClickListener { apply(RotationMode.AUTO) }
        binding.btnPortrait.setOnClickListener { apply(RotationMode.PORTRAIT) }
        binding.btnLandscape.setOnClickListener { apply(RotationMode.LANDSCAPE) }
        binding.btnReversePortrait.setOnClickListener { apply(RotationMode.REVERSE_PORTRAIT) }
        binding.btnReverseLandscape.setOnClickListener { apply(RotationMode.REVERSE_LANDSCAPE) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = Settings.canDrawOverlays(this)
        binding.tvPermission.text =
            getString(if (granted) R.string.permission_granted else R.string.permission_missing)
        binding.btnGrant.visibility = if (granted) View.GONE else View.VISIBLE
        binding.tvStatus.text = getString(R.string.current_mode, modeLabel(RotationPrefs.load(this)))
    }

    private fun apply(mode: RotationMode) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        requestNotificationPermissionIfNeeded()
        RotationService.setMode(this, mode)
        binding.tvStatus.text = getString(R.string.current_mode, modeLabel(mode))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
    }

    private fun modeLabel(mode: RotationMode): String = when (mode) {
        RotationMode.AUTO -> getString(R.string.mode_auto)
        RotationMode.PORTRAIT -> getString(R.string.mode_portrait)
        RotationMode.LANDSCAPE -> getString(R.string.mode_landscape)
        RotationMode.REVERSE_PORTRAIT -> getString(R.string.mode_reverse_portrait)
        RotationMode.REVERSE_LANDSCAPE -> getString(R.string.mode_reverse_landscape)
    }

    private companion object {
        const val REQ_NOTIFICATION = 1
    }}
