package com.jlindemann.science.utils

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.jlindemann.science.R
import java.io.File

/**
 * An answer as a branded image (roadmap 2.10): the question, the answer and its card, under the
 * Atomic mark, shared through the system sheet.
 *
 * Drawn from `share_answer_image.xml` off screen, at a fixed width in dp, so an image looks the same
 * whichever screen shared it. The card is a snapshot of the one in the conversation, so it is exactly
 * what the user saw.
 */
object AnswerShareImage {

    /** The width the image is laid out at; about a phone's, so the card snapshot is not scaled much. */
    private const val WIDTH_DP = 400

    /** Where shared images are written, and the name the FileProvider path declares. */
    private const val DIRECTORY = "shared"

    /**
     * @param card the answer's card on screen, or null for none. The caller passes null for a locked
     *   card, so a share never shows what the paywall hides.
     */
    fun render(activity: Activity, question: String?, answer: CharSequence, card: View?): Bitmap {
        val root = LayoutInflater.from(activity).inflate(R.layout.share_answer_image, null, false)
        root.findViewById<TextView>(R.id.share_question).apply {
            text = question.orEmpty()
            visibility = if (question.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        root.findViewById<TextView>(R.id.share_answer).text = answer
        val snapshot = card?.takeIf { it.isLaidOut && it.width > 0 && it.height > 0 }?.drawToBitmap()
        root.findViewById<ImageView>(R.id.share_card).apply {
            setImageBitmap(snapshot)
            visibility = if (snapshot == null) View.GONE else View.VISIBLE
        }

        val width = (WIDTH_DP * activity.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val bitmap = Bitmap.createBitmap(root.measuredWidth, root.measuredHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        return bitmap
    }

    /** Write [bitmap] to the cache and open the share sheet with it. */
    fun share(activity: Activity, bitmap: Bitmap) {
        val directory = File(activity.cacheDir, DIRECTORY).apply { mkdirs() }
        // One file, overwritten: the cache holds the latest share only.
        val file = File(directory, "atomic_answer.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // The ClipData is what lets the sheet show a preview of the image.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)))
    }
}
