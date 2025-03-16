package com.jlindemann.science.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.adapter.AchievementAdapter
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.ConstantsModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference

class UserActivity : BaseActivity(), AchievementAdapter.OnAchievementClickListener {
    private var achievementsList = ArrayList<Achievement>()
    private var mAdapter = AchievementAdapter(achievementsList, this, this)
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
            }
        } else {
            setTheme(if (themePrefValue == 0) R.style.AppTheme else R.style.AppThemeDark)
        }
        setContentView(R.layout.activity_user)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_achievements)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        ConstantsModel.getList(ArrayList())
        setupRecyclerView()

        findViewById<FrameLayout>(R.id.view_user).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        setupTitleController()
        setupBackButton()
        setupStats()

        val proPref = ProVersion(this).getValue()
        // Update depending on PRO or Not:
        if (proPref == 1) {
            // Pro-specific setup
        }
        if (proPref == 100) {
            // Pro-specific setup
        }

        val sharedPref = getSharedPreferences("UserActivityPrefs", Context.MODE_PRIVATE)
        val userTitle = sharedPref.getString("user_title", "Default Title")
        findViewById<TextView>(R.id.user_title_downstate).text = userTitle

        findViewById<TextView>(R.id.user_title_downstate).setOnClickListener {
            showEditTextPopup()
        }

        // Handle persistable permissions
        val uriString = sharedPref.getString("user_img_uri", null)
        uriString?.let {
            val uri = Uri.parse(it)
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                Log.e("UserActivity", "Failed to take persistable URI permission onCreate", e)
            }
        }
    }

    private fun setupTitleController() {
        findViewById<FrameLayout>(R.id.common_title_user_color).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.user_title).visibility = View.INVISIBLE
        findViewById<FrameLayout>(R.id.common_title_back_user).elevation = resources.getDimension(R.dimen.zero_elevation)
        findViewById<ScrollView>(R.id.user_scroll).viewTreeObserver.addOnScrollChangedListener {
            if (findViewById<ScrollView>(R.id.user_scroll).scrollY > 150) {
                findViewById<FrameLayout>(R.id.common_title_user_color).visibility = View.VISIBLE
                findViewById<TextView>(R.id.user_title).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.user_img).visibility = View.VISIBLE
                findViewById<FrameLayout>(R.id.common_title_back_user).elevation = resources.getDimension(R.dimen.one_elevation)
            } else {
                findViewById<FrameLayout>(R.id.common_title_user_color).visibility = View.INVISIBLE
                findViewById<TextView>(R.id.user_title).visibility = View.VISIBLE
                findViewById<TextView>(R.id.user_title).visibility = View.INVISIBLE
                findViewById<FrameLayout>(R.id.common_title_back_user).elevation = resources.getDimension(R.dimen.zero_elevation)
            }
        }
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            onBackPressed()
        }
    }


    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_achievements)
        AchievementModel.getList(this, achievementsList)
        achievementsList.sortByDescending { it.progress.toDouble() / it.maxProgress.toDouble() }
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val adapter = AchievementAdapter(achievementsList, this, this)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun showEditTextPopup() {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_edit_text, null)
        val editText = popupView.findViewById<EditText>(R.id.editText)
        val buttonOk = popupView.findViewById<Button>(R.id.buttonOk)

        val alertDialog = AlertDialog.Builder(this)
            .setView(popupView)
            .create()

        buttonOk.setOnClickListener {
            val newText = editText.text.toString()
            findViewById<TextView>(R.id.user_title_downstate).text = newText
            saveUserTitle(newText)
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun saveUserTitle(title: String) {
        val sharedPref = getSharedPreferences("UserActivityPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_title", title)
            apply()
        }
    }

    private fun setupStats() {
        val statistics = ArrayList<Statistics>()
        StatisticsModel.getList(this, statistics)
        findViewById<TextView>(R.id.elements_stat).text = statistics[0].progress.toString()
        findViewById<TextView>(R.id.calculation_stat).text = statistics[1].progress.toString()
        findViewById<TextView>(R.id.search_stat).text = statistics[2].progress.toString()


    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val params = findViewById<FrameLayout>(R.id.common_title_back_user).layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        findViewById<FrameLayout>(R.id.common_title_back_user).layoutParams = params

        val params2 = findViewById<ImageView>(R.id.user_img).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<ImageView>(R.id.user_img).layoutParams = params2
    }

    override fun achievementClickListener(item: Achievement, position: Int) {
        // TODO: Not yet implemented
    }
}