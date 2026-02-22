package com.refresh.auto

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.SeekBar
import android.app.Activity
import android.util.Log

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RefreshApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val intervalText = findViewById<TextView>(R.id.intervalText)
        val intervalSeek = findViewById<SeekBar>(R.id.intervalSeek)
        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val chkMonitor = findViewById<CheckBox>(R.id.chkMonitor)
        val chkUpload = findViewById<CheckBox>(R.id.chkUpload)
        val scriptStatus = findViewById<TextView>(R.id.scriptStatus)

        intervalSeek.progress = 60
        intervalText.text = "Interval: 60s"

        intervalSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val interval = if (progress < 10) 10 else progress
                intervalText.text = "Interval: ${interval}s"
                RefreshService.intervalSeconds = interval
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnStart.setOnClickListener {
            val xIntent = packageManager.getLaunchIntentForPackage("com.twitter.android")
                ?: packageManager.getLaunchIntentForPackage("com.x.android")
            if (xIntent != null) {
                startActivity(xIntent)
            }
            RefreshService.isRunning = true
            statusText.text = "Status: RUNNING"
        }

        btnStop.setOnClickListener {
            RefreshService.isRunning = false
            statusText.text = "Status: STOPPED"
        }

        chkMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                scriptStatus.text = "Starting twitter_monitor.sh..."
                handler.postDelayed({
                    runScript("/data/data/com.termux/files/home/twitter_monitor.sh", scriptStatus)
                }, 2000)
            }
        }

        chkUpload.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                scriptStatus.text = "Starting upload.sh..."
                handler.postDelayed({
                    runScript("/data/data/com.termux/files/home/upload.sh", scriptStatus)
                }, 2000)
            }
        }
    }

    private fun runScript(path: String, statusView: TextView) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod +x $path && $path"))
                val exitCode = process.waitFor()
                val name = path.substringAfterLast("/")
                handler.post {
                    if (exitCode == 0) {
                        statusView.text = "$name started"
                        Log.d(TAG, "$name executed OK")
                    } else {
                        val err = process.errorStream.bufferedReader().readText()
                        statusView.text = "$name error (code $exitCode)"
                        Log.e(TAG, "$name failed: $err")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    statusView.text = "Error: ${e.message}"
                    Log.e(TAG, "Script error", e)
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = if (RefreshService.isRunning) "Status: RUNNING" else "Status: STOPPED"
    }
}
