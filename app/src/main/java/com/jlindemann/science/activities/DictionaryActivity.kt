package com.jlindemann.science.activities

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Insets
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.adapter.DictionaryAdapter
import com.jlindemann.science.model.Dictionary
import com.jlindemann.science.model.DictionaryModel
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.Utils
import kotlinx.android.synthetic.main.activity_dictionary.*
import kotlinx.android.synthetic.main.activity_dictionary.close_iso_search
import kotlinx.android.synthetic.main.activity_dictionary.edit_iso
import kotlinx.android.synthetic.main.activity_dictionary.search_bar_iso
import kotlinx.android.synthetic.main.activity_dictionary.search_btn
import kotlinx.android.synthetic.main.activity_dictionary.title_box
import java.util.*
import kotlin.collections.ArrayList


class DictionaryActivity : BaseActivity(), DictionaryAdapter.OnDictionaryClickListener {
    private var dictionaryList = ArrayList<Dictionary>()
    var mAdapter = DictionaryAdapter(dictionaryList, this, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.gestureSetup(window)

        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()

        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { setTheme(R.style.AppTheme) }
                Configuration.UI_MODE_NIGHT_YES -> { setTheme(R.style.AppThemeDark) }
            }
        }
        if (themePrefValue == 0) { setTheme(R.style.AppTheme) }
        if (themePrefValue == 1) { setTheme(R.style.AppThemeDark) }
        setContentView(R.layout.activity_dictionary) //REMEMBER: Never move any function calls above this

        recyclerView()
        clickSearch()

        view_dic.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        back_btn_d.setOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int) {
        val params = rc_view.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        rc_view.layoutParams = params

        val params2 = common_title_back_dic.layoutParams as ViewGroup.LayoutParams
        params2.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        common_title_back_dic.layoutParams = params2
    }

    private fun recyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rc_view)
        val dictionaryList = ArrayList<Dictionary>()

        DictionaryModel.getList(dictionaryList)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = DictionaryAdapter(dictionaryList, this, this)
        recyclerView.adapter = adapter

        dictionaryList.sortWith(Comparator { lhs, rhs ->
            if (lhs.heading < rhs.heading) -1 else if (lhs.heading < rhs.heading) 1 else 0
        })

        adapter.notifyDataSetChanged()

        edit_iso.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {}
            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ){}

            override fun afterTextChanged(s: Editable) {
                filter(s.toString(), dictionaryList, recyclerView)
            }
        })
    }

    private fun filter(text: String, list: ArrayList<Dictionary>, recyclerView: RecyclerView) {
        val filteredList: ArrayList<Dictionary> = ArrayList()
        for (item in list) {
            if (item.heading.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                filteredList.add(item)
                Log.v("SSDD2", filteredList.toString())
            }
        }
        mAdapter.filterList(filteredList)
        mAdapter.notifyDataSetChanged()
        recyclerView.adapter = DictionaryAdapter(filteredList, this, this)
    }

    private fun clickSearch() {
        search_btn.setOnClickListener {
            Utils.fadeInAnim(search_bar_iso, 150)

            val delayOpen = Handler()
            delayOpen.postDelayed({
                Utils.fadeOutAnim(title_box, 150)
            }, 151)

            edit_iso.requestFocus()
            val imm: InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_iso, InputMethodManager.SHOW_IMPLICIT)
        }
        close_iso_search.setOnClickListener {
            Utils.fadeOutAnim(search_bar_iso, 150)

            val delayClose = Handler()
            delayClose.postDelayed({
                Utils.fadeInAnim(title_box, 150)
            }, 151)

            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun dictionaryClickListener(item: Dictionary, wiki: Button, url: String, position: Int) {
        wiki.setOnClickListener {
            val packageNameString = "com.android.chrome"
            val customTabBuilder = CustomTabsIntent.Builder()

            customTabBuilder.setToolbarColor(ContextCompat.getColor(this, R.color.wikipediaColor))
            customTabBuilder.setSecondaryToolbarColor(ContextCompat.getColor(this ,R.color.wikipediaColor))
            customTabBuilder.setShowTitle(true)

            val CustomTab = customTabBuilder.build()
            val intent = CustomTab.intent
            intent.data = Uri.parse(url)

            val packageManager = packageManager
            val resolveInfoList = packageManager.queryIntentActivities(CustomTab.intent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (TextUtils.equals(packageName, packageNameString))
                    CustomTab.intent.setPackage(packageNameString)
            }
            CustomTab.intent.data?.let { it1 -> CustomTab.launchUrl(this, it1) }
        }
    }


}



