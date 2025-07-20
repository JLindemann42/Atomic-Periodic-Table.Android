import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GameResultItem(
    val question: String,
    val pickedAnswer: String?,
    val correctAnswer: String,
    val wasCorrect: Boolean,
    val baseXp: Int
) : Parcelable