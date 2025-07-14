import android.view.View

fun fadeInEffectOverlay(effectOverlay: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
    effectOverlay.alpha = 0f
    effectOverlay.visibility = View.VISIBLE
    effectOverlay.animate()
        .alpha(1f)
        .setDuration(duration)
        .withEndAction { onEnd?.invoke() }
        .start()
}

fun fadeOutEffectOverlay(effectOverlay: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
    effectOverlay.animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            effectOverlay.visibility = View.GONE
            onEnd?.invoke()
        }
        .start()
}