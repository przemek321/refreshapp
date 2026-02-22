package com.refresh.auto

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import android.widget.SeekBar
import android.app.Activity
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RefreshApp"
    private lateinit var consoleOutput: TextView
    private lateinit var consoleScroll: ScrollView

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
        val btnClearLog = findViewById<Button>(R.id.btnClearLog)
        consoleOutput = findViewById(R.id.consoleOutput)
        consoleScroll = findViewById(R.id.consoleScroll)

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

        btnClearLog.setOnClickListener {
            consoleOutput.text = ""
        }

        chkMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                scriptStatus.text = "Starting twitter_monitor.sh..."
                logToConsole(">>> Starting twitter_monitor.sh (2s delay)...")
                handler.postDelayed({
                    runScript("/data/data/com.termux/files/home/twitter_monitor.sh", scriptStatus)
                }, 2000)
            } else {
                scriptStatus.text = "twitter_monitor.sh stopped"
                logToConsole(">>> Stopped twitter_monitor.sh checkbox")
            }
        }

        chkUpload.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                scriptStatus.text = "Starting upload.sh..."
                logToConsole(">>> Starting upload.sh (2s delay)...")
                handler.postDelayed({
                    runScript("/data/data/com.termux/files/home/upload.sh", scriptStatus)
                }, 2000)
            } else {
                scriptStatus.text = "upload.sh stopped"
                logToConsole(">>> Stopped upload.sh checkbox")
            }
        }
    }

    private fun logToConsole(line: String) {
        handler.post {
            consoleOutput.append(line + "\n")
            consoleScroll.post { consoleScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun runScript(path: String, statusView: TextView) {
        val name = path.substringAfterLast("/")
        val logFile = "/data/local/tmp/${name}.log"
        Thread {
            try {
                val termuxBash = "/data/data/com.termux/files/usr/bin/bash"
                val termuxPrefix = "/data/data/com.termux/files/usr"
                val termuxHome = "/data/data/com.termux/files/home"
                val cmd = "export PREFIX=$termuxPrefix; " +
                    "export HOME=$termuxHome; " +
                    "export PATH=$termuxPrefix/bin:$termuxPrefix/bin/applets:\$PATH; " +
                    "export LD_LIBRARY_PATH=$termuxPrefix/lib; " +
                    "echo CHECK_SCRIPT: && ls -la $path; " +
                    "echo CHECK_BASH: && ls -la $termuxBash; " +
                    "chmod +x $path; " +
                    "$termuxBash $path >$logFile 2>&1; " +
                    "echo EXIT_CODE:\$?"
                logToConsole("$ running $name ...")
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val outputLine = line ?: ""
                    logToConsole(outputLine)
                }

                // Also read stderr from su itself
                val errReader = BufferedReader(InputStreamReader(process.errorStream))
                while (errReader.readLine().also { line = it } != null) {
                    logToConsole("[stderr] ${line ?: ""}")
                }

                val exitCode = process.waitFor()
                logToConsole(">>> su exited with code $exitCode")

                // Read the log file for full script output
                logToConsole(">>> Reading $name log:")
                val logProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $logFile"))
                val logReader = BufferedReader(InputStreamReader(logProcess.inputStream))
                while (logReader.readLine().also { line = it } != null) {
                    logToConsole("  ${line ?: ""}")
                }
                logProcess.waitFor()

                handler.post {
                    if (exitCode == 0) {
                        statusView.text = "$name finished OK"
                    } else {
                        statusView.text = "$name exit code $exitCode"
                    }
                }
            } catch (e: Exception) {
                logToConsole(">>> ERROR: ${e.message}")
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
