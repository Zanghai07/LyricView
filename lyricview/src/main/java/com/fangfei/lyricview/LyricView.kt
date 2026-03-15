
/*
 * Copyright (c) 2026 Zanghai (github.com/Zanghai07)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fangfei.lyricview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class LyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var currentTextSize: Float = 58f
        set(value) { field = value; buildLayouts(); invalidate() }

    var normalTextSize: Float = 48f
        set(value) { field = value; buildLayouts(); invalidate() }

    var currentAlpha: Int = 255
        set(value) { field = value.coerceIn(0, 255); invalidate() }

    var pastAlpha: Int = 60
        set(value) { field = value.coerceIn(0, 255); invalidate() }

    var futureAlpha: Int = 120
        set(value) { field = value.coerceIn(0, 255); invalidate() }

    var normalColor: Int = Color.WHITE
        set(value) { field = value; normalPaint.color = value; invalidate() }

    var currentColor: Int = Color.WHITE
        set(value) { field = value; highlightPaint.color = value; invalidate() }

    var lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER
        set(value) { field = value; buildLayouts(); invalidate() }

    private var lyrics: List<String> = emptyList()
    private var currentLine = -1
    private var previousLine = -1

    private val lineSpacingMultiplier = 1.4f
    private val blockSpacingExtra = 1f
    private var scaleProgress = 0f

    private var currentScrollOffset = 0f
    private var targetScrollOffset = 0f

    private val linePhases = mutableListOf<Float>()
    private val lineStaggerStart = mutableListOf<Float>()
    private val lineScrollFrom = mutableListOf<Float>()
    private val lineScrollTo = mutableListOf<Float>()

    private var scrollAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null
    private var customTypeface: Typeface? = null

    private val normalPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        alpha = 100
    }

    private val highlightPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val drawPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }

    private val layouts = mutableListOf<StaticLayout?>()
    private val lineHeights = mutableListOf<Float>()

    private val baseLayoutTextSize: Float
        get() = max(currentTextSize, normalTextSize)

    private var timestamps: List<Long> = emptyList()
    private var onSeekListener: ((timestampMs: Long) -> Unit)? = null
    private var onUserScrollStateChanged: ((isScrolling: Boolean) -> Unit)? = null

    private var isUserScrolling = false
    private val autoSnapHandler = Handler(Looper.getMainLooper())
    private val autoSnapDelay = 3000L

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging = false
    private var touchDownY = 0f
    private var touchDownX = 0f
    private var lastTouchY = 0f
    private var touchMoved = false
    private var touchDownTime = 0L
    private val tapTimeout = 200L
    private val tapSlop = touchSlop * 1.5f

    private var isOverScrolling = false
    private var overScrollAmount = 0f
    private val maxOverScroll = 300f
    private var lastMoveTime = 0L
    private var velocity = 0f

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.LyricView)
            try {
                currentTextSize = ta.getDimension(R.styleable.LyricView_currentTextSize, currentTextSize)
                normalTextSize = ta.getDimension(R.styleable.LyricView_normalTextSize, normalTextSize)
                currentAlpha = ta.getInt(R.styleable.LyricView_currentAlpha, currentAlpha)
                pastAlpha = ta.getInt(R.styleable.LyricView_pastAlpha, pastAlpha)
                futureAlpha = ta.getInt(R.styleable.LyricView_futureAlpha, futureAlpha)
                normalColor = ta.getColor(R.styleable.LyricView_normalColor, normalColor)
                currentColor = ta.getColor(R.styleable.LyricView_currentColor, currentColor)
                lyricsAlignment = when (ta.getInt(R.styleable.LyricView_lyricsAlignment, 1)) {
                    0 -> Layout.Alignment.ALIGN_NORMAL
                    2 -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_CENTER
                }
            } finally {
                ta.recycle()
            }
        }
        updateTypeface()
    }

    fun setCustomFont(fontResourceId: Int) {
        try {
            customTypeface = ResourcesCompat.getFont(context, fontResourceId)
            updateTypeface(); buildLayouts(); invalidate()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setCustomFont(typeface: Typeface) {
        customTypeface = typeface
        updateTypeface(); buildLayouts(); invalidate()
    }

    fun resetToDefaultFont() {
        customTypeface = null
        updateTypeface(); buildLayouts(); invalidate()
    }

    fun setOnSeekListener(listener: (timestampMs: Long) -> Unit) { onSeekListener = listener }
    fun setTimestamps(timestamps: List<Long>) { this.timestamps = timestamps }
    fun setOnUserScrollStateChanged(listener: (Boolean) -> Unit) { onUserScrollStateChanged = listener }

    fun setLyrics(lyrics: List<String>) {
        this.lyrics = lyrics
        currentLine = -1
        previousLine = -1
        currentScrollOffset = 0f
        targetScrollOffset = 0f
        isUserScrolling = false
        buildLayouts()
        resetLineData()
        invalidate()
    }

    fun setCurrentLine(lineIndex: Int) {
        if (lyrics.isEmpty()) return
        if (lineIndex == -1) {
            if (currentLine == -1) return
            previousLine = -1
            currentLine = -1
            scaleProgress = 0f
            invalidate()
            return
        }
        if (lineIndex == currentLine) return
        previousLine = currentLine
        currentLine = lineIndex.coerceIn(0, lyrics.size - 1)
        startScaleAnimation()
        if (isUserScrolling) return
        targetScrollOffset = calculateTargetScrollOffset()
        startStaggeredScrollAnimation()
    }

    private fun resetLineData() {
        linePhases.clear()
        lineStaggerStart.clear()
        lineScrollFrom.clear()
        lineScrollTo.clear()
        repeat(lyrics.size) {
            linePhases.add(1f)
            lineStaggerStart.add(0f)
            lineScrollFrom.add(0f)
            lineScrollTo.add(0f)
        }
    }

    private fun updateTypeface() {
        val tf = customTypeface ?: Typeface.DEFAULT
        normalPaint.typeface = tf
        highlightPaint.typeface = customTypeface ?: Typeface.DEFAULT_BOLD
        drawPaint.typeface = tf
    }

    private fun calculateTargetScrollOffset(): Float {
        if (lyrics.isEmpty() || currentLine < 0) return 0f
        val centerY = height / 2f
        var totalHeightBefore = 0f
        for (i in 0 until currentLine) totalHeightBefore += getBlockHeight(i)
        val currentBlockCenter = totalHeightBefore + getBlockHeight(currentLine) / 2f
        return min(0f, centerY - currentBlockCenter)
    }

    private fun getBlockHeight(index: Int) = lineHeights.getOrNull(index) ?: 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y
                touchDownX = event.x
                touchDownTime = System.currentTimeMillis()
                lastTouchY = event.y
                touchMoved = false
                isDragging = false
                lastMoveTime = System.currentTimeMillis()
                velocity = 0f
                cancelAutoSnap()
                scrollAnimator?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                if (!isDragging && abs(event.y - touchDownY) > touchSlop) {
                    isDragging = true
                    touchMoved = true
                    enterUserScrollMode()
                }
                if (isDragging) {
                    val proposed = currentScrollOffset + dy
                    val clamped = clampScrollOffset(proposed)
                    if (clamped == proposed) {
                        isOverScrolling = false
                        overScrollAmount = 0f
                        currentScrollOffset = clamped
                        targetScrollOffset = clamped
                    } else {
                        isOverScrolling = true
                        overScrollAmount += dy
                        val ratio = (1f - abs(overScrollAmount) / maxOverScroll).coerceIn(0.05f, 1f)
                        currentScrollOffset += dy * ratio * 0.5f
                        targetScrollOffset = currentScrollOffset
                    }
                    for (i in lineScrollFrom.indices) {
                        lineScrollFrom[i] = currentScrollOffset
                        lineScrollTo[i] = currentScrollOffset
                        linePhases[i] = 1f
                    }
                    val now = System.currentTimeMillis()
                    velocity = dy / (now - lastMoveTime).coerceAtLeast(1) * 16f
                    lastMoveTime = now
                    invalidate()
                }
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tapDuration = System.currentTimeMillis() - touchDownTime
                val totalDist = hypot((event.x - touchDownX).toDouble(), (event.y - touchDownY).toDouble())
                val isValidTap = !touchMoved && totalDist < tapSlop && tapDuration < tapTimeout

                if (isValidTap) {
                    val tappedIndex = getLineIndexAtY(event.y)
                    if (tappedIndex != null && tappedIndex in timestamps.indices) {
                        isUserScrolling = false
                        cancelAutoSnap()
                        onUserScrollStateChanged?.invoke(false)
                        previousLine = currentLine
                        currentLine = tappedIndex
                        targetScrollOffset = calculateTargetScrollOffset()
                        startScaleAnimation()
                        startStaggeredScrollAnimation()
                        onSeekListener?.invoke(timestamps[tappedIndex])
                        return true
                    }
                }

                if (isUserScrolling) {
                    if (isOverScrolling) {
                        bounceBackFromOverScroll()
                        isOverScrolling = false
                        overScrollAmount = 0f
                    } else if (abs(velocity) > 2f) {
                        applyFling()
                    }
                    scheduleAutoSnap()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun bounceBackFromOverScroll() =
        animateScrollTo(clampScrollOffset(currentScrollOffset), 500L, OvershootInterpolator(0.8f), false)

    private fun applyFling() =
        animateScrollTo(clampScrollOffset(currentScrollOffset + velocity * 15f), 400L, DecelerateInterpolator(2f), false)

    private fun getTotalContentHeight(): Float {
        var total = 0f
        for (h in lineHeights) total += h
        return total
    }

    private fun getScrollBounds(): Pair<Float, Float> {
        val contentHeight = getTotalContentHeight()
        val centerY = height / 2f
        val maxScroll = centerY - getBlockHeight(0) / 2f
        val minScroll = centerY - (contentHeight - getBlockHeight(lyrics.size - 1) / 2f)
        return Pair(minScroll, maxScroll)
    }

    private fun clampScrollOffset(offset: Float): Float {
        if (lyrics.isEmpty() || lineHeights.isEmpty()) return offset
        val (mn, mx) = getScrollBounds()
        return offset.coerceIn(mn, mx)
    }

    private fun enterUserScrollMode() {
        if (!isUserScrolling) {
            isUserScrolling = true
            onUserScrollStateChanged?.invoke(true)
        }
    }

    private fun exitUserScrollMode() {
        isUserScrolling = false
        cancelAutoSnap()
        onUserScrollStateChanged?.invoke(false)
        snapToCurrentLine()
    }

    private fun scheduleAutoSnap() {
        cancelAutoSnap()
        autoSnapHandler.postDelayed({ exitUserScrollMode() }, autoSnapDelay)
    }

    private fun cancelAutoSnap() = autoSnapHandler.removeCallbacksAndMessages(null)

    private fun snapToCurrentLine() {
        targetScrollOffset = calculateTargetScrollOffset()
        animateScrollTo(targetScrollOffset, 600L, DecelerateInterpolator(2f), true)
    }

    private fun animateScrollTo(
        target: Float,
        duration: Long,
        interpolator: android.view.animation.Interpolator,
        stagger: Boolean
    ) {
        scrollAnimator?.cancel()
        val fromScroll = currentScrollOffset
        val scrollingUp = target < fromScroll

        for (i in lineScrollFrom.indices) {
            lineScrollFrom[i] = currentScrollOffset
            lineScrollTo[i] = target
            linePhases[i] = 0f
            lineStaggerStart[i] = if (!stagger) {
                0f
            } else if (scrollingUp) {
                when {
                    i == currentLine -> 0f
                    i < currentLine -> ((1f - (currentLine - i).coerceAtMost(8) / 8f) * 0.10f).coerceIn(0f, 0.10f)
                    else -> (0.06f + (i - currentLine) * 0.09f).coerceIn(0.06f, 0.45f)
                }
            } else {
                when {
                    i == currentLine -> 0f
                    i > currentLine -> ((1f - (i - currentLine).coerceAtMost(8) / 8f) * 0.10f).coerceIn(0f, 0.10f)
                    else -> (0.06f + (currentLine - i) * 0.09f).coerceIn(0.06f, 0.45f)
                }
            }
        }

        scrollAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { anim ->
                val globalProgress = anim.animatedValue as Float
                currentScrollOffset = fromScroll + (target - fromScroll) * globalProgress
                for (i in lineScrollFrom.indices) {
                    val ss = lineStaggerStart.getOrNull(i) ?: 0f
                    val remaining = 1f - ss
                    linePhases[i] = if (remaining <= 0f) 1f
                    else ((globalProgress - ss) / remaining).coerceIn(0f, 1f)
                }
                invalidate()
            }
            doOnEnd {
                currentScrollOffset = target
                for (i in linePhases.indices) linePhases[i] = 1f
                invalidate()
            }
            start()
        }
    }

    private fun startStaggeredScrollAnimation() =
        animateScrollTo(targetScrollOffset, 800L, DecelerateInterpolator(2f), true)

    private fun startScaleAnimation() {
        scaleAnimator?.cancel()
        scaleProgress = 0f
        scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scaleProgress = it.animatedValue as Float
                if (scrollAnimator?.isRunning != true) invalidate()
            }
            doOnEnd { if (scrollAnimator?.isRunning != true) invalidate() }
            start()
        }
    }

    private fun getCurrentTextSize(index: Int): Float = when (index) {
        currentLine -> normalTextSize + (currentTextSize - normalTextSize) * scaleProgress
        previousLine -> currentTextSize - (currentTextSize - normalTextSize) * scaleProgress
        else -> normalTextSize
    }

    private fun getAlphaForLine(index: Int): Int = when {
        currentLine == -1 -> futureAlpha
        index == currentLine ->
            (futureAlpha + (currentAlpha - futureAlpha) * scaleProgress).toInt()
                .coerceIn(minOf(futureAlpha, currentAlpha), maxOf(futureAlpha, currentAlpha))
        index == previousLine -> {
            val targetAlpha = if (index < currentLine) pastAlpha else futureAlpha
            (currentAlpha - (currentAlpha - targetAlpha) * scaleProgress).toInt()
                .coerceIn(minOf(targetAlpha, currentAlpha), maxOf(targetAlpha, currentAlpha))
        }
        index < currentLine -> pastAlpha
        else -> futureAlpha
    }

    private fun buildLayouts() {
        layouts.clear()
        lineHeights.clear()
        if (lyrics.isEmpty() || width == 0) return

        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val measurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = normalColor
            typeface = customTypeface ?: Typeface.DEFAULT
            textSize = baseLayoutTextSize
            textAlign = Paint.Align.LEFT
            alpha = 255
        }

        for (lyric in lyrics) {
            val wrappedLyric = smartWrap(lyric, measurePaint, availableWidth)
            val layout = createStaticLayout(wrappedLyric, measurePaint, availableWidth, lyricsAlignment)
            layouts.add(layout)
            val lineCount = layout?.lineCount ?: 1
            val baseHeight = layout?.height?.toFloat() ?: (baseLayoutTextSize * lineCount)
            val totalHeight = if (lineCount == 1) baseLayoutTextSize * lineSpacingMultiplier
                              else baseHeight + baseLayoutTextSize * 0.1f
            lineHeights.add(totalHeight + baseLayoutTextSize * blockSpacingExtra)
        }

        resetLineData()
    }

    private fun smartWrap(text: String, paint: TextPaint, availableWidth: Int): String {
        if (text.contains('\n')) return text
        if (paint.measureText(text) <= availableWidth) return text

        val mid = text.length / 2
        var left = mid
        var right = mid

        while (left > 0 || right < text.length) {
            if (right < text.length) {
                if (text[right] == ' ') return text.substring(0, right) + '\n' + text.substring(right + 1)
                right++
            }
            if (left > 0) {
                left--
                if (text[left] == ' ') return text.substring(0, left) + '\n' + text.substring(left + 1)
            }
        }

        return text
    }

    private fun getLineIndexAtY(tapY: Float): Int? {
        var yOffset = 100f
        for (i in lyrics.indices) {
            val blockHeight = lineHeights.getOrNull(i) ?: continue
            val phase = linePhases.getOrNull(i) ?: 1f
            val fromScroll = lineScrollFrom.getOrNull(i) ?: currentScrollOffset
            val toScroll = lineScrollTo.getOrNull(i) ?: currentScrollOffset
            val top = yOffset + fromScroll + (toScroll - fromScroll) * phase
            val scale = getCurrentTextSize(i) / baseLayoutTextSize
            val scaledH = blockHeight * scale
            val scaleDiff = (scaledH - blockHeight) / 2f
            if (tapY in (top - scaleDiff)..(top + blockHeight + scaleDiff)) return i
            yOffset += blockHeight
        }
        return null
    }

    private fun createStaticLayout(
        text: String, paint: TextPaint, width: Int, align: Layout.Alignment
    ): StaticLayout? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(align).setLineSpacing(0f, 1f).setIncludePad(false).build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, width, align, 1f, 0f, false)
            }
        } catch (e: Throwable) { e.printStackTrace(); null }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty()) return

        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        var yOffset = 100f

        lyrics.forEachIndexed { index, _ ->
            val layout = layouts.getOrNull(index) ?: run {
                yOffset += lineHeights.getOrNull(index) ?: 0f
                return@forEachIndexed
            }
            val blockHeight = lineHeights.getOrNull(index) ?: 0f
            val phase = linePhases.getOrNull(index) ?: 1f
            val fromScroll = lineScrollFrom.getOrNull(index) ?: currentScrollOffset
            val toScroll = lineScrollTo.getOrNull(index) ?: currentScrollOffset
            val top = yOffset + fromScroll + (toScroll - fromScroll) * phase
            val bottom = top + blockHeight

            if (bottom > 0 && top < height) {
                val scale = getCurrentTextSize(index) / baseLayoutTextSize
                val dx = paddingLeft + (availableWidth - layout.width) / 2f

                drawPaint.set(if (index == currentLine) highlightPaint else normalPaint)
                drawPaint.alpha = getAlphaForLine(index)
                drawPaint.textSize = baseLayoutTextSize
                layout.paint.set(drawPaint)

                canvas.save()
                canvas.translate(dx, top)
                canvas.scale(scale, scale, layout.width / 2f, blockHeight / 2f)
                layout.draw(canvas)
                canvas.restore()
            }

            yOffset += blockHeight
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) buildLayouts()
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = action()
    })
}