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
    private var restartIntervalMinutes = 30
    private var autoRestartRunnable: Runnable? = null

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

        val btnRestartX = findViewById<Button>(R.id.btnRestartX)
        val restartIntervalText = findViewById<TextView>(R.id.restartIntervalText)
        val restartIntervalSeek = findViewById<SeekBar>(R.id.restartIntervalSeek)
        val chkAutoRestart = findViewById<CheckBox>(R.id.chkAutoRestart)

        restartIntervalSeek.progress = 30
        restartIntervalText.text = "Restart every: 30 min"

        restartIntervalSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mins = if (progress < 5) 5 else progress
                restartIntervalMinutes = mins
                restartIntervalText.text = "Restart every: ${mins} min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnRestartX.setOnClickListener {
            restartX()
        }

        chkAutoRestart.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                logToConsole(">>> Auto restart X enabled (every ${restartIntervalMinutes} min)")
                startAutoRestart()
            } else {
                logToConsole(">>> Auto restart X disabled")
                stopAutoRestart()
            }
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

    private fun restartX() {
        logToConsole(">>> Restarting X...")
        Thread {
            try {
                val pkg = "com.twitter.android"
                val altPkg = "com.x.android"
                val stopProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $pkg; am force-stop $altPkg"))
                stopProc.waitFor()
                handler.post { logToConsole(">>> X killed, reopening in 2s...") }
                Thread.sleep(2000)
                handler.post {
                    val xIntent = packageManager.getLaunchIntentForPackage(pkg)
                        ?: packageManager.getLaunchIntentForPackage(altPkg)
                    if (xIntent != null) {
                        startActivity(xIntent)
                        logToConsole(">>> X reopened")
                    } else {
                        logToConsole(">>> ERROR: X app not found")
                    }
                }
            } catch (e: Exception) {
                handler.post { logToConsole(">>> ERROR: ${e.message}") }
                Log.e(TAG, "Restart X error", e)
            }
        }.start()
    }

    private fun startAutoRestart() {
        stopAutoRestart()
        autoRestartRunnable = object : Runnable {
            override fun run() {
                logToConsole(">>> Auto restarting X (every ${restartIntervalMinutes} min)...")
                restartX()
                handler.postDelayed(this, restartIntervalMinutes * 60 * 1000L)
            }
        }
        handler.postDelayed(autoRestartRunnable!!, restartIntervalMinutes * 60 * 1000L)
    }

    private fun stopAutoRestart() {
        autoRestartRunnable?.let { handler.removeCallbacks(it) }
        autoRestartRunnable = null
    }

    private fun logToConsole(line: String) {
        handler.post {
            consoleOutput.append(line + "\n")
            consoleScroll.post { consoleScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun runScript(path: String, statusView: TextView) {
        val name = path.substringAfterLast("/")
        logToConsole("$ launching $name via Termux (root)...")
        Thread {
            try {
                // First ensure Termux is running by launching it
                val launchCmd = "am start -n com.termux/.app.TermuxActivity"
                val launchProc = Runtime.getRuntime().exec(arrayOf("su", "-c", launchCmd))
                launchProc.waitFor()
                Thread.sleep(1000)

                // Now start the script via Termux RUN_COMMAND using foreground service
                val cmd = "am start-foreground-service" +
                    " --user 0" +
                    " -n com.termux/.app.RunCommandService" +
                    " -a com.termux.RUN_COMMAND" +
                    " --es com.termux.RUN_COMMAND_PATH '$path'" +
                    " --es com.termux.RUN_COMMAND_WORKDIR '/data/data/com.termux/files/home'" +
                    " --ez com.termux.RUN_COMMAND_BACKGROUND true"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errReader = BufferedReader(InputStreamReader(process.errorStream))
                val output = reader.readText()
                val errors = errReader.readText()
                val exitCode = process.waitFor()
                handler.post {
                    if (exitCode == 0) {
                        logToConsole(">>> $name sent to Termux OK")
                        if (output.isNotBlank()) logToConsole(output.trim())
                        statusView.text = "$name launched via Termux"
                    } else {
                        logToConsole(">>> ERROR (exit $exitCode): $errors")
                        if (output.isNotBlank()) logToConsole(output.trim())
                        statusView.text = "Error launching $name"
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    logToConsole(">>> ERROR: ${e.message}")
                    statusView.text = "Error: ${e.message}"
                }
                Log.e(TAG, "Script error", e)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = if (RefreshService.isRunning) "Status: RUNNING" else "Status: STOPPED"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRestart()
    }
}
