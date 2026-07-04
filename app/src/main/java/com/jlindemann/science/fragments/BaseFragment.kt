package com.jlindemann.science.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.jlindemann.science.activities.MainActivity

abstract class BaseFragment : Fragment() {
    
    fun getMainActivity(): MainActivity? = activity as? MainActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Request insets dispatch to handle specific fragment UI
        view.requestApplyInsets()
    }
}
