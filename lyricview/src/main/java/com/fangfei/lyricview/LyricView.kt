package com.fangfei.lyricview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lyrics: List<String> = emptyList()
    private var currentLine = 0
    private var previousLine = -1

    // Config: Sesuaikan selera lu di sini
    private val baseTextSize = 54f
    private val maxTextSize = 72f
    private val lineSpacing = 1.8f // Jarak antar baris

    private var scaleProgress = 0f
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    
    private var scaleAnimator: ValueAnimator? = null
    private var scrollAnimator: ValueAnimator? = null

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        alpha = 100 // Lirik biasa dibikin agak pudar
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700") // Kuning Emas
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setLyrics(lyrics: List<String>) {
        this.lyrics = lyrics
        currentLine = 0
        previousLine = -1
        scrollOffset = 0f
        targetScrollOffset = 0f
        invalidate()
    }

    fun setCurrentLine(lineIndex: Int) {
        if (lyrics.isEmpty() || lineIndex == currentLine) return

        previousLine = currentLine
        currentLine = lineIndex.coerceIn(0, lyrics.size - 1)

        // LOGIKA KUNCI: Hitung target scroll agar baris aktif SELALU di tengah layar
        targetScrollOffset = calculateTargetScrollOffset()
        
        startScaleAnimation()
        startScrollAnimation()
    }

    private fun calculateTargetScrollOffset(): Float {
        if (lyrics.isEmpty()) return 0f

        val centerY = height / 2f
        val lineHeight = maxTextSize * lineSpacing
        
        // Posisi Y baris aktif relatif terhadap lirik pertama
        val currentLineY = currentLine * lineHeight + (lineHeight / 2f)

        // Kita geser canvas ke atas (negatif) sebanyak selisih posisi baris dengan tengah layar
        val target = centerY - currentLineY
        
        // Biar baris-baris awal gak langsung lompat ke tengah, kita limit maksimal di 0
        // Lirik baru akan mulai "naik" ke tengah setelah highlight-nya nyampe tengah layar
        return min(0f, target)
    }

    private fun startScaleAnimation() {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scaleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startScrollAnimation() {
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(scrollOffset, targetScrollOffset).apply {
            duration = 800 // Durasi scroll agak lama biar smooth
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun getCurrentTextSize(index: Int): Float {
        return when (index) {
            currentLine -> baseTextSize + (maxTextSize - baseTextSize) * scaleProgress
            previousLine -> maxTextSize - (maxTextSize - baseTextSize) * scaleProgress
            else -> baseTextSize
        }
    }

    private fun getAlphaForLine(index: Int): Int {
        return when (index) {
            currentLine -> (100 + (155 * scaleProgress)).toInt() 
            previousLine -> (255 - (155 * scaleProgress)).toInt()
            else -> 100
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty()) return

        val centerX = width / 2f
        val lineHeight = maxTextSize * lineSpacing

        lyrics.forEachIndexed { index, lyric ->
            // Posisi Y = Urutan baris + Jarak + Geseran Scroll
            val y = (index * lineHeight) + (lineHeight / 2f) + scrollOffset
            
            // Render kalau masuk area layar aja
            if (y > -maxTextSize && y < height + maxTextSize) {
                val textSize = getCurrentTextSize(index)
                val alpha = getAlphaForLine(index)
                
                val paint = if (index == currentLine) highlightPaint else normalPaint
                paint.textSize = textSize
                paint.alpha = alpha
                
                canvas.drawText(lyric, centerX, y, paint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        // Wajib setMeasuredDimension dengan tinggi layar biar centerY akurat
        setMeasuredDimension(width, height)
    }
}