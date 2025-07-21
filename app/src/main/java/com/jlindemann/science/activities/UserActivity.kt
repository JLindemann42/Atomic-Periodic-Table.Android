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
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
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
        rateSetup()
        shareSetup()

        val proPref = ProVersion(this).getValue()
        // Update depending on PRO or Not:
        if (proPref == 1) {
            findViewById<TextView>(R.id.pro_badge).text = "NON-PRO"
        }
        if (proPref == 100) {
            findViewById<TextView>(R.id.pro_badge).text = "PRO-USER"
        }

        val sharedPref = getSharedPreferences("UserActivityPrefs", Context.MODE_PRIVATE)
        val userTitle = sharedPref.getString("user_title", "User Page")
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

    private fun rateSetup() {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                findViewById<TextView>(R.id.rate_btn).setOnClickListener {
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        // The flow has finished. The API does not indicate whether the user
                        // reviewed or not, or even whether the review dialog was shown. Thus, no
                        // matter the result, we continue our app flow.
                        Log.d("UserActivity", "In-app review flow finished.")
                    }
                }
            } else {
                // There was some problem, log or handle the error.
                val exception = task.exception
                if (exception != null) {
                    if (exception is ReviewException) {
                        // It's a ReviewException, you can safely access errorCode
                        @ReviewErrorCode val reviewErrorCode = exception.errorCode
                        Log.e("UserActivity", "Failed to request review flow. ReviewException ErrorCode: $reviewErrorCode", exception)
                        // TODO: Handle specific ReviewErrorCodes if needed
                        // For example:
                        // when (reviewErrorCode) {
                        //     ReviewErrorCode.INVALID_REQUEST -> Log.e("UserActivity", "Invalid request for review flow.")
                        //     // Add other cases as needed
                        // }
                    } else {
                        // It's some other type of exception
                        Log.e("UserActivity", "Failed to request review flow with an unexpected error.", exception)
                    }
                } else {
                    // Task failed but no exception was provided (should be rare)
                    Log.e("UserActivity", "Failed to request review flow, but no exception was provided in the task.")
                }
            }
        }
    }

    private fun shareSetup() {
        findViewById<TextView>(R.id.share_btn).setOnClickListener {
            val share = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "'Atomic' - The open-source Periodic Table! Get it now via the following link: https://play.google.com/store/apps/details?id=com.jlindemann.science")
                type = "text/plain"
            }, null)
            startActivity(share)
        }
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