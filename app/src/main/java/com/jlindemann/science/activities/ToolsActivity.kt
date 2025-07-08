package com.jlindemann.science.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tools.CalculatorActivity
import com.jlindemann.science.activities.tools.ChemicalReactionsActivity
import com.jlindemann.science.activities.tools.FlashCardActivity
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ThemePreference

class ToolsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        setContentView(R.layout.activity_tools)

        findViewById<FrameLayout>(R.id.view_tools).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        //Title Controller
        findViewById<FrameLayout>(R.id.common_title_tool_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.tools_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
        findViewById<ScrollView>(R.id.tools_scroll).getViewTreeObserver()
            .addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
                var y = 300f
                override fun onScrollChanged() {
                    if (findViewById<ScrollView>(R.id.tools_scroll).getScrollY() > 150) {
                        findViewById<FrameLayout>(R.id.common_title_tool_color).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tools_title).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tools_title_downstate).visibility = View.INVISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.one_elevation))
                    } else {
                        findViewById<FrameLayout>(R.id.common_title_tool_color).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.tools_title).visibility = View.INVISIBLE
                        findViewById<TextView>(R.id.tools_title_downstate).visibility = View.VISIBLE
                        findViewById<FrameLayout>(R.id.common_title_back_tab).elevation = (resources.getDimension(R.dimen.zero_elevation))
                    }
                    y = findViewById<ScrollView>(R.id.tools_scroll).getScrollY().toFloat()
                }
            })

        toolListeners()
        mostUsedBar()

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            this.onBackPressed()
        }

    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
            val params = findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams as ViewGroup.LayoutParams
            params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            findViewById<FrameLayout>(R.id.common_title_back_tab).layoutParams = params

            val params2 = findViewById<TextView>(R.id.tools_title_downstate).layoutParams as ViewGroup.MarginLayoutParams
            params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            findViewById<TextView>(R.id.tools_title_downstate).layoutParams = params2
    }

    private fun mostUsedBar() {
        val mostUsedToolPreference = MostUsedToolPreference(this)
        val regex = Regex("(\\w{3})=(\\d\\.\\d)") // Corrected regex pattern
        val matches = regex.findAll(mostUsedToolPreference.getValue())
            .map { it.groups[1]!!.value to it.groups[2]!!.value.toDouble() }
            .toList()
        val sortedValues = matches.sortedByDescending { it.second }

        val textView1: TextView = findViewById(R.id.mostT_1)
        val textView2: TextView = findViewById(R.id.mostT_2)

        val textViewList = listOf(textView1, textView2)

        sortedValues.forEachIndexed { index, pair ->
            if (index < textViewList.size) {
                // Setup TextViews
                when (pair.first) {
                    "cal" -> textViewList[index].text = getString(R.string.cal)
                    "rec" -> textViewList[index].text = getString(R.string.rec)
                }

                textViewList[index].setOnClickListener {
                    val activity = when (pair.first) {
                        "cal" -> CalculatorActivity::class.java
                        "rec" -> ChemicalReactionsActivity::class.java
                        else -> null
                    }
                    activity?.let {
                        val intent = Intent(this, it)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun toolListeners() {
        //Calculator
        findViewById<FrameLayout>(R.id.tool_calculator).setOnClickListener {
            val intent = Intent(this, CalculatorActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.calculator_btn).setOnClickListener {
            val intent = Intent(this, CalculatorActivity::class.java)
            startActivity(intent)
        }

        //Flashcard game
        findViewById<FrameLayout>(R.id.tool_flashcards).setOnClickListener {
            val intent = Intent(this, FlashCardActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.tool_flashcards_btn).setOnClickListener {
            val intent = Intent(this, FlashCardActivity::class.java)
            startActivity(intent)
        }
    }

}



