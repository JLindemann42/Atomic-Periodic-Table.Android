package com.jlindemann.science.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.utils.AnalyticsHelper
import com.jlindemann.science.utils.AnalyticsSource

abstract class BaseFragment : Fragment() {

    companion object {
        /** Argument `MainActivity.switchFragment` uses to say how this entry was triggered. */
        const val ARG_ENTRY_SOURCE = "analytics_entry_source"
    }

    /** Reported as the screen name. Overridable for a fragment whose class name is not the label. */
    open val analyticsScreenName: String
        get() = javaClass.simpleName.removeSuffix("Fragment")

    private var firstEntryLogged = false

    fun getMainActivity(): MainActivity? = activity as? MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Request insets dispatch to handle specific fragment UI
        view.requestApplyInsets()
    }

    /**
     * Count entering this fragment.
     *
     * `onResume` rather than `onViewCreated` because coming back from another activity is a real
     * entry too, and it is what [com.jlindemann.science.activities.BaseActivity] already counts for
     * activities. Those returns are tagged [AnalyticsSource.RETURN] so they can be separated from
     * a deliberate tab switch — the switch itself is only ever the first resume, since
     * `switchFragment` builds a new instance every time and keeps no back stack.
     */
    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        val source =
            if (firstEntryLogged) AnalyticsSource.RETURN
            else arguments?.getString(ARG_ENTRY_SOURCE) ?: AnalyticsSource.UNKNOWN
        firstEntryLogged = true
        AnalyticsHelper.logFragmentView(ctx, analyticsScreenName, javaClass.simpleName, source)
    }

    open fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {}
}
