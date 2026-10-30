package com.jlindemann.science.views

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Obscures a locked chat card.
 *
 * A real blur only exists as a cheap view-level effect from API 31; this app supports API 24. Rather
 * than pull in RenderScript — deprecated, and heavy for something purely decorative — the fallback
 * is a scrim over the card. Both read as "there is something here you cannot see yet", which is the
 * point of the treatment: the card still occupies its space and still hints at its shape, so the
 * upsell is about something visible rather than an empty box.
 *
 * The content behind is still bound. That is deliberate — the moment the user upgrades, clearing the
 * effect is enough, with no re-bind and no reload.
 */
object ProCardGate {

    /** Radius in pixels. Enough that no figure or label survives it. */
    private const val BLUR_RADIUS = 28f

    /** How opaque the pre-31 scrim is. High enough to hide values, low enough to keep the shape. */
    const val SCRIM_ALPHA = 0.92f

    /** Obscure [cardView], or reveal it again when [locked] is false. */
    fun apply(cardView: View?, locked: Boolean) {
        if (cardView == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cardView.setRenderEffect(
                if (locked) {
                    RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
            // The blur alone is the whole treatment on API 31+; no scrim needed.
            cardView.alpha = 1f
        } else {
            // No view-level blur available. Fade the card back hard so the scrim above it does the
            // obscuring, and the card reads as a shape rather than as data.
            cardView.alpha = if (locked) 0.35f else 1f
        }
    }

    /** True when the scrim view above the card is needed on this device. */
    fun needsScrim(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
}
