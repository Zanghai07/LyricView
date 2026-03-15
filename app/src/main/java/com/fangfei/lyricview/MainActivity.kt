package com.fangfei.lyricview.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fangfei.lyricview.LyricView

class MainActivity : AppCompatActivity() {
    
    private lateinit var lyricView: LyricView
    private lateinit var btnStart: Button
    private lateinit var btnReset: Button
    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var currentLine = 0
    private var isPlaying = false
    private val sampleLyrics = listOf(
        "Mungkin suatu nanti",
        "Kita kan bertemu lagi",
        "Dalam cerita yang berbeda",
        "Namun rasa yang sama",
        "",
        "Kau dan aku tahu",
        "Bahwa cinta takkan pernah mati",
        "Hanya berganti wajah",
        "Dalam hidup yang baru",
        "",
        "Dan di setiap hela nafas",
        "Kusebut namamu",
        "Walau kau takkan dengar",
        "Namun hati selalu tahu",
        "",
        "Mungkin suatu nanti",
        "Kita kan bertemu lagi",
        "Dalam cerita yang berbeda",
        "Namun rasa yang sama",
        "",
        "Kau dan aku tahu",
        "Bahwa cinta takkan pernah mati",
        "Hanya berganti wajah",
        "Dalam hidup yang baru",
        "",
        "Dan di setiap hela nafas",
        "Kusebut namamu",
        "Walau kau takkan dengar",
        "Namun hati selalu tahu",
        "",
        "Hanya berganti wajah",
        "Dalam hidup yang baru",
        "Mungkin suatu nanti..."
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        lyricView = findViewById(R.id.lyric_view)
        btnStart = findViewById(R.id.btn_start)
        btnReset = findViewById(R.id.btn_reset)
        tvStatus = findViewById(R.id.tv_status)
        
        lyricView.setLyrics(sampleLyrics)
        updateStatus()
        
        btnStart.setOnClickListener {
            if (isPlaying) {
                pauseSimulation()
            } else {
                startSimulation()
            }
        }
        
        btnReset.setOnClickListener {
            resetSimulation()
        }
    }
    
    private fun startSimulation() {
        isPlaying = true
        btnStart.text = "Pause"
        btnReset.isEnabled = false
        simulateNextLine()
    }
    
    private fun pauseSimulation() {
        isPlaying = false
        btnStart.text = "Resume"
        btnReset.isEnabled = true
        handler.removeCallbacksAndMessages(null)
        updateStatus()
    }
    
    private fun resetSimulation() {
        currentLine = 0
        lyricView.setCurrentLine(0)
        updateStatus()
    }
    
    private fun simulateNextLine() {
        if (!isPlaying) return
        
        lyricView.setCurrentLine(currentLine)
        updateStatus()
        currentLine++
        
        if (currentLine < sampleLyrics.size) {
            handler.postDelayed({
                simulateNextLine()
            }, 1800)
        } else {
            isPlaying = false
            btnStart.text = "Start"
            btnReset.isEnabled = true
            tvStatus.text = "Finished - All lines played"
        }
    }
    
    private fun updateStatus() {
        tvStatus.text = if (isPlaying) {
            "Playing: Line ${currentLine + 1}/${sampleLyrics.size}"
        } else {
            if (currentLine == 0) {
                "Ready to start"
            } else {
                "Paused at line ${currentLine + 1}/${sampleLyrics.size}"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}