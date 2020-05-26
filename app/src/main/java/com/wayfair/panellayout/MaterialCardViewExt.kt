package com.wayfair.panellayout

import android.animation.ValueAnimator
import com.google.android.material.card.MaterialCardView

fun MaterialCardView.animateRadius(from: Float, to: Float) =
    ValueAnimator.ofFloat(from, to).apply {
        addUpdateListener {
            radius = it.animatedValue as Float
        }
    }.start()


fun MaterialCardView.animateElevation(from: Float, to: Float) =
    ValueAnimator.ofFloat(from, to).apply {
        addUpdateListener {
            elevation = it.animatedValue as Float
        }
    }.start()
