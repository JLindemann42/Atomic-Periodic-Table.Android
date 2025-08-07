import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jlindemann.science.R
import com.jlindemann.science.activities.settings.ProActivity

class ProUpgradeDialogFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This makes the dialog dismissible by back press and background click
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Upgrade button
        view.findViewById<Button>(R.id.popup_action_button)?.setOnClickListener {
            startActivity(Intent(requireContext(), ProActivity::class.java))
            dismiss()
        }

        // "Not today" button
        view.findViewById<Button>(R.id.popup_secondary_button)?.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        fun show(activity: FragmentActivity) {
            ProUpgradeDialogFragment().show(activity.supportFragmentManager, "ProUpgradeDialog")
        }
    }
}