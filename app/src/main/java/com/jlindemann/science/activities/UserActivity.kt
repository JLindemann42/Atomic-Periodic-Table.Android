package com.jlindemann.science.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.firebase.auth.FirebaseAuth
import com.jlindemann.science.R
import com.jlindemann.science.adapter.AchievementAdapter
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.model.Achievement
import com.jlindemann.science.model.AchievementModel
import com.jlindemann.science.model.ConstantsModel
import com.jlindemann.science.model.Statistics
import com.jlindemann.science.model.StatisticsModel
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.sync.ProgressSyncManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UserActivity : BaseActivity(), AchievementAdapter.OnAchievementClickListener {
    private val TAG = "UserActivity"

    private var achievementsList = ArrayList<Achievement>()
    private lateinit var recyclerView: RecyclerView
    private var mAdapter: AchievementAdapter? = null

    private lateinit var btnSignOut: TextView
    private lateinit var tvUserInfo: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var userImg: ImageView

    // Using lazy so FirebaseApp is initialized in Application.onCreate before we get the instance
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // Legacy sign-in launcher (fallback)
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                tvSyncStatus.text = getString(R.string.signing_in)
                AuthManager.handleIdTokenForFirebase(idToken) { success, exception ->
                    runOnUiThread {
                        if (success) {
                            // update UI from Firebase currentUser and sync progress (if allowed)
                            updateUi()
                        } else {
                            tvSyncStatus.text = getString(R.string.sign_in_failed)
                            Log.w(TAG, "Firebase sign-in failed", exception)
                            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                tvSyncStatus.text = getString(R.string.sign_in_failed)
                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            tvSyncStatus.text = getString(R.string.sign_in_failed)
            if (e.statusCode == 10) {
                Log.e(TAG, "Google Sign-In failed with DEVELOPER_ERROR (10). This usually means the SHA-1 of your signing certificate is not registered in the Firebase/Google Cloud Console, or the package name is incorrect.", e)
            } else {
                Log.w(TAG, "Google sign-in failed with code ${e.statusCode}", e)
            }
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tvSyncStatus.text = getString(R.string.sign_in_failed)
            Log.e(TAG, "Google sign-in unexpected error", e)
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // One Tap (Identity) fields
    private lateinit var oneTapClient: SignInClient
    private lateinit var oneTapRequest: BeginSignInRequest

    // One Tap launcher (modern sliding up account selector)
    private val oneTapLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val credential: SignInCredential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (!idToken.isNullOrEmpty()) {
                    tvSyncStatus.text = getString(R.string.signing_in)
                    AuthManager.handleIdTokenForFirebase(idToken) { success, exception ->
                        runOnUiThread {
                            if (success) {
                                // update UI and trigger sync if allowed
                                updateUi()
                            } else {
                                tvSyncStatus.text = getString(R.string.sign_in_failed)
                                Log.w(TAG, "Firebase sign-in failed (one-tap)", exception)
                                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // no id token - silent / fallback if desired
                    tvSyncStatus.text = getString(R.string.sign_in_failed)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "One Tap sign-in handling failed", t)
                tvSyncStatus.text = getString(R.string.sign_in_failed)
            }
        }
    }

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

        //Setting up UI and adding sync status
        userImg = findViewById(R.id.user_img)
        tvUserInfo = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionMenuTextColor)).let { ta -> val color = ta.getColor(0, currentTextColor); ta.recycle(); color })
        }
        tvSyncStatus = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionMenuTextColor)).let { ta -> val color = ta.getColor(0, currentTextColor); ta.recycle(); color })
        }

        // insert status text under pro_badge if possible
        try {
            val proBadge = findViewById<TextView>(R.id.pro_badge)
            val parent = proBadge.parent as? ViewGroup
            parent?.let {
                val index = it.indexOfChild(proBadge)
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.gravity = Gravity.CENTER_HORIZONTAL
                it.addView(tvUserInfo, index + 1, params)
                val params2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params2.gravity = Gravity.CENTER_HORIZONTAL
                it.addView(tvSyncStatus, index + 2, params2)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add dynamic UI elements", e)
        }

        btnSignOut = findViewById(R.id.signout_button)
        try {
            val proBadge = findViewById<TextView>(R.id.pro_badge)
            val parent = proBadge.parent as? ViewGroup
            parent?.addView(btnSignOut)
        } catch (_: Exception) { /* ignore */ }

        // achievements list setup
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view_achievements)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        setupRecyclerView()

        findViewById<FrameLayout>(R.id.view_user).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        setupTitleController()
        setupBackButton()
        setupStats()
        rateSetup()
        shareSetup()

        // PRO badge text
        val proPref = ProVersion(this).getValue()
        val proPlusPref = ProPlusVersion(this).getValue()
        if (proPref == 1) findViewById<TextView>(R.id.pro_badge).text = getString(R.string.non_pro)
        if (proPref == 100) findViewById<TextView>(R.id.pro_badge).text = getString(R.string.pro_user)
        if (proPlusPref == 100) findViewById<TextView>(R.id.pro_badge).text = getString(R.string.pro_plus_user)

        // Replace user title views with user's name (they will be updated from updateUi)
        // initialize with a default placeholder
        setUserTitleViews("User Page")

        // legacy persisted image uri
        val sharedPref = getSharedPreferences("UserActivityPrefs", Context.MODE_PRIVATE)
        val uriString = sharedPref.getString("user_img_uri", null)
        uriString?.let {
            try {
                val uri = Uri.parse(it)
                userImg.setImageURI(uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set persisted user image uri", e)
            }
        }

        // Initialize One Tap sign-in (Identity) to provide sliding-up account selector
        try {
            oneTapClient = Identity.getSignInClient(this)
            oneTapRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.web_client_id))
                        .setFilterByAuthorizedAccounts(false) // show account selector
                        .build()
                )
                .setAutoSelectEnabled(false)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "OneTap initialization failed, legacy fallback will be used", t)
        }

        findViewById<TextView>(R.id.login_button).setOnClickListener {
            // Try One Tap
            var oneTapStarted = false
            try {
                if (::oneTapClient.isInitialized) {
                    oneTapClient.beginSignIn(oneTapRequest)
                        .addOnSuccessListener { result ->
                            try {
                                val intentSender = result.pendingIntent.intentSender
                                oneTapLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (t: Throwable) {
                                Log.w(TAG, "OneTap pending intent launch failed", t)
                                // fall back to legacy if available
                                startLegacySignIn()
                            }
                        }
                        .addOnFailureListener { _ ->
                            // fall back to legacy
                            startLegacySignIn()
                        }
                    oneTapStarted = true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "OneTap beginSignIn failed", t)
            }

            if (!oneTapStarted) {
                startLegacySignIn()
            }
        }

        // profile image click: sign-in/out (use One Tap first, fallback to legacy)
        userImg.setOnClickListener {
            if (AuthManager.isSignedIn()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.sign_out)
                    .setMessage(R.string.confirm_sign_out)
                    .setPositiveButton(R.string.sign_out) { _, _ ->
                        AuthManager.signOut(null) {
                            runOnUiThread { updateUiForSignOut() }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                // Try One Tap
                var oneTapStarted = false
                try {
                    if (::oneTapClient.isInitialized) {
                        oneTapClient.beginSignIn(oneTapRequest)
                            .addOnSuccessListener { result ->
                                try {
                                    val intentSender = result.pendingIntent.intentSender
                                    oneTapLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                } catch (t: Throwable) {
                                    Log.w(TAG, "OneTap pending intent launch failed", t)
                                    // fall back to legacy if available
                                    startLegacySignIn()
                                }
                            }
                            .addOnFailureListener { _ ->
                                // fall back to legacy
                                startLegacySignIn()
                            }
                        oneTapStarted = true
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "OneTap beginSignIn failed", t)
                }

                if (!oneTapStarted) {
                    startLegacySignIn()
                }
            }
        }

        btnSignOut.setOnClickListener {
            AuthManager.signOut(null) {
                runOnUiThread { updateUiForSignOut() }
            }
        }

        updateUi()
    }

    private fun startLegacySignIn() {
        try {
            val webClientId = getString(R.string.web_client_id)
            val googleClient = AuthManager.buildGoogleSignInClient(this, webClientId)
            val signInIntent = googleClient.signInIntent
            signInLauncher.launch(signInIntent)
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy GoogleSignIn fallback failed", t)
        }
    }

    private fun setUserTitleViews(name: String) {
        val downState = findViewById<TextView>(R.id.user_title_downstate)
        val upState = findViewById<TextView>(R.id.user_title)
        downState?.text = name
        upState?.text = name
    }

    private fun updateUi() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val displayName = user.displayName ?: user.email ?: "User Page"
            setUserTitleViews(displayName)
            btnSignOut.visibility = View.VISIBLE
            findViewById<TextView>(R.id.login_button).visibility = View.GONE
            val photo = user.photoUrl
            if (photo != null) {
                loadImageFromUrlIntoImageView(photo.toString(), userImg)
            } else {
                userImg.setImageResource(R.drawable.ic_account)
            }
            // Trigger sync after sign-in (only if user has PRO/PRO+)
            onSigninSuccess()
        } else {
            updateUiForSignOut()
        }
    }

    private fun updateUiForSignOut() {
        tvUserInfo.text = getString(R.string.user_signed_out)
        tvSyncStatus.text = ""
        btnSignOut.visibility = View.GONE
        userImg.setImageResource(R.drawable.ic_account)
        setUserTitleViews("User Page")
    }

    /**
     * Called after Firebase sign-in is established (or when UI is refreshed while signed in).
     * Reads FirebaseAuth.currentUser for uid/photo/name and performs progress sync.
     *
     * Sync is only performed for Pro or Pro+ users. All users may sign in, but only sync for paying users.
     */
    private fun onSigninSuccess() {
        val uid = AuthManager.getUid()
        if (uid == null) {
            tvSyncStatus.text = getString(R.string.sign_in_failed)
            return
        }
        findViewById<TextView>(R.id.login_button).visibility = View.GONE
        tvUserInfo.text = "Logged in"

        // Check Pro/Pro+ status and only run sync if user has Pro or Pro+
        val proPref = ProVersion(this).getValue()
        val proPlusPref = ProPlusVersion(this).getValue()
        val isProOrProPlus = (proPref == 100) || (proPlusPref == 100)


        if (!isProOrProPlus) {
            // Allow sign-in but do not sync for non-Pro users.
            tvSyncStatus.text = "Get PRO or PRO+ to enable sync" // clear sync status for non-pro user
            return
        }

        // perform sync for Pro/Pro+ users
        tvSyncStatus.text = getString(R.string.syncing_progress)
        ProgressSyncManager.mergeAndUploadLocalProgress(this, uid) { success ->
            runOnUiThread {
                tvSyncStatus.text = if (success) getString(R.string.sync_complete) else getString(R.string.sync_failed)
                // Always refresh the achievements/statistics view after a sync attempt
                setupRecyclerView()
                setupStats()
            }
        }
    }

    private fun loadImageFromUrlIntoImageView(urlString: String, imageView: ImageView) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.doInput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                val input = conn.inputStream
                val bmp = BitmapFactory.decodeStream(input)
                runOnUiThread {
                    if (bmp != null) imageView.setImageBitmap(bmp)
                }
                input.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to load profile image", t)
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
        achievementsList.clear()
        AchievementModel.getList(this, achievementsList)
        // ensure each achievement loads its persisted progress
        achievementsList.forEach { it.loadProgress(this) }
        achievementsList.sortByDescending { it.progress.toDouble() / it.maxProgress.toDouble() }
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        mAdapter = AchievementAdapter(achievementsList, this, this)
        recyclerView.adapter = mAdapter
        mAdapter?.notifyDataSetChanged()
    }

    private fun setupStats() {
        val statistics = ArrayList<Statistics>()
        StatisticsModel.getList(this, statistics)
        statistics.forEach { it.loadProgress(this) }
        if (statistics.size >= 3) {
            findViewById<TextView>(R.id.elements_stat).text = statistics[0].progress.toString()
            findViewById<TextView>(R.id.calculation_stat).text = statistics[1].progress.toString()
            findViewById<TextView>(R.id.search_stat).text = statistics[2].progress.toString()
        } else {
            // clear if missing
            findViewById<TextView>(R.id.elements_stat).text = "0"
            findViewById<TextView>(R.id.calculation_stat).text = "0"
            findViewById<TextView>(R.id.search_stat).text = "0"
        }
    }

    private fun rateSetup() {
        val manager = com.google.android.play.core.review.ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                findViewById<TextView>(R.id.rate_btn).setOnClickListener {
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener {
                        Log.d(TAG, "In-app review flow finished.")
                    }
                }
            } else {
                val exception = task.exception
                if (exception != null) {
                    Log.w(TAG, "Request review flow failed", exception)
                } else {
                    Log.w(TAG, "Request review flow failed with unknown error")
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

        val params2 = findViewById<CardView>(R.id.user_img_container).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
        findViewById<CardView>(R.id.user_img_container).layoutParams = params2
    }

    override fun achievementClickListener(item: Achievement, position: Int) {
        // Not implemented yet
    }
}