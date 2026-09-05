package com.gintama.novabrowser.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java.text.NumberFormat
import java.util.Locale

/**
 * NovaMotion: High-performance, hardware-accelerated motion graphics and animation framework
 * for NovaBrowser. Powers ambient celestial floating, tactile spring haptics, kinetic shield
 * notifications, fluid canvas cross-fades, and numerical telemetry count-up transitions.
 */
object NovaMotion {

    /**
     * Ambient celestial hover & breathing glow loop for the Start Canvas Hero Identity.
     */
    fun startHeroBreathingAnimation(logoView: View, glowView: View? = null): AnimatorSet {
        val density = logoView.resources.displayMetrics.density
        val animators = mutableListOf<Animator>()

        // Subtle vertical floating motion
        val floatY = ObjectAnimator.ofFloat(logoView, "translationY", 0f, -7f * density, 0f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        animators.add(floatY)

        // Hero logo micro-scale breathing
        val scaleLogoX = ObjectAnimator.ofFloat(logoView, "scaleX", 1.0f, 1.03f, 1.0f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleLogoY = ObjectAnimator.ofFloat(logoView, "scaleY", 1.0f, 1.03f, 1.0f).apply {
            duration = 2800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        animators.add(scaleLogoX)
        animators.add(scaleLogoY)

        // Cosmic Aura Glow pulsing
        if (glowView != null) {
            val glowAlpha = ObjectAnimator.ofFloat(glowView, "alpha", 0.30f, 0.85f, 0.30f).apply {
                duration = 2800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val glowScaleX = ObjectAnimator.ofFloat(glowView, "scaleX", 0.92f, 1.14f, 0.92f).apply {
                duration = 2800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val glowScaleY = ObjectAnimator.ofFloat(glowView, "scaleY", 0.92f, 1.14f, 0.92f).apply {
                duration = 2800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            animators.add(glowAlpha)
            animators.add(glowScaleX)
            animators.add(glowScaleY)
        }

        return AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    /**
     * Tactile Spring Micro-Interactions: provides physics-based scale-down on press
     * and bouncy overshoot spring on release. Returns false so onClickListeners still fire.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachSpringTouchFeedback(vararg views: View) {
        for (v in views) {
            v.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        target.animate()
                            .scaleX(0.92f)
                            .scaleY(0.92f)
                            .setDuration(90L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(240L)
                            .setInterpolator(OvershootInterpolator(2.8f))
                            .start()
                    }
                }
                false
            }
        }
    }

    /**
     * Kinetic Shield Badge Pulse: pops with an energetic bounce when trackers are neutralized.
     */
    fun pulseBadge(view: View) {
        view.animate().cancel()
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.animate()
            .scaleX(1.32f)
            .scaleY(1.32f)
            .setDuration(110L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220L)
                    .setInterpolator(OvershootInterpolator(3.2f))
                    .start()
            }
            .start()
    }

    /**
     * Smooth Cinematic Cross-Fade between View containers (e.g. Start Canvas <-> WebView).
     */
    fun crossFade(outView: View, inView: View, durationMs: Long = 200L) {
        if (outView == inView) return

        outView.animate().cancel()
        inView.animate().cancel()

        outView.animate()
            .alpha(0f)
            .translationY(-12f)
            .setDuration(durationMs)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                outView.visibility = View.GONE
                outView.alpha = 1f
                outView.translationY = 0f
            }
            .start()

        inView.alpha = 0f
        inView.translationY = 16f
        inView.visibility = View.VISIBLE
        inView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    /**
     * Hardware-accelerated Progress Bar animation with smooth FastOutSlow interpolation.
     */
    fun animateProgressBar(progressBar: ProgressBar, targetProgress: Int) {
        if (targetProgress in 1..99) {
            if (progressBar.visibility != View.VISIBLE) {
                progressBar.alpha = 1f
                progressBar.visibility = View.VISIBLE
            }
            val anim = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, targetProgress)
            anim.duration = 180L
            anim.interpolator = FastOutSlowInInterpolator()
            anim.start()
        } else if (targetProgress >= 100) {
            val anim = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, 100)
            anim.duration = 100L
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progressBar.animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction {
                            progressBar.visibility = View.GONE
                            progressBar.progress = 0
                            progressBar.alpha = 1f
                        }
                        .start()
                }
            })
            anim.start()
        } else {
            progressBar.visibility = View.GONE
            progressBar.progress = 0
        }
    }

    /**
     * Rolling Number Count-Up Ticker for blocked statistics.
     */
    fun animateCountUp(textView: TextView, targetVal: Long, suffix: String = "") {
        if (targetVal <= 0L) {
            textView.text = "0 $suffix".trim()
            return
        }

        val formatter = NumberFormat.getNumberInstance(Locale.US)
        val safeTarget = targetVal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val animator = ValueAnimator.ofInt(0, safeTarget).apply {
            duration = 650L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                val current = anim.animatedValue as Int
                val formatted = formatter.format(current)
                textView.text = if (suffix.isNotBlank()) "$formatted $suffix" else formatted
            }
        }
        animator.start()
    }

    /**
     * Slide Down Entrance for Find-In-Page bar.
     */
    fun slideDown(view: View, durationMs: Long = 220L) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = -view.height.toFloat().coerceAtLeast(-120f)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Slide Up Exit for Find-In-Page bar.
     */
    fun slideUp(view: View, durationMs: Long = 180L, onComplete: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .translationY(-view.height.toFloat().coerceAtLeast(-120f))
            .setDuration(durationMs)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.translationY = 0f
                view.alpha = 1f
                onComplete?.invoke()
            }
            .start()
    }
}
