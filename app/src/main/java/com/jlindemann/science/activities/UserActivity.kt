package com.jlindemann.science.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.card.MaterialCardView
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
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.preferences.LastSyncPreference
import com.jlindemann.science.preferences.WindowsTierPreference
import com.jlindemann.science.sync.ProgressSyncManager
import com.jlindemann.science.sync.AutoSync
import com.jlindemann.science.utils.EntitlementManager
import com.jlindemann.science.utils.EntitlementRules
import com.jlindemann.science.utils.Tier
import com.jlindemann.science.utils.UnifiedTitleBarController

class UserActivity : BaseActivity(), AchievementAdapter.OnAchievementClickListener {
    private val TAG = "UserActivity"

    private lateinit var titleBar: UnifiedTitleBarController
    private var achievementsList = ArrayList<Achievement>()
    private lateinit var recyclerView: RecyclerView
    private var mAdapter: AchievementAdapter? = null

    private lateinit var btnSignOut: MaterialButton
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
                setSyncStatus(getString(R.string.signing_in))
                AuthManager.handleIdTokenForFirebase(idToken) { success, exception ->
                    runOnUiThread {
                        if (success) {
                            // update UI from Firebase currentUser and sync progress (if allowed)
                            updateUi()
                        } else {
                            setSyncStatus(getString(R.string.sign_in_failed))
                            Log.w(TAG, "Firebase sign-in failed", exception)
                            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                setSyncStatus(getString(R.string.sign_in_failed))
                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            setSyncStatus(getString(R.string.sign_in_failed))
            if (e.statusCode == 10) {
                Log.e(TAG, "Google Sign-In failed with DEVELOPER_ERROR (10). This usually means the SHA-1 of your signing certificate is not registered in the Firebase/Google Cloud Console, or the package name is incorrect.", e)
            } else {
                Log.w(TAG, "Google sign-in failed with code ${e.statusCode}", e)
            }
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            setSyncStatus(getString(R.string.sign_in_failed))
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
                    setSyncStatus(getString(R.string.signing_in))
                    AuthManager.handleIdTokenForFirebase(idToken) { success, exception ->
                        runOnUiThread {
                            if (success) {
                                // update UI and trigger sync if allowed
                                updateUi()
                            } else {
                                setSyncStatus(getString(R.string.sign_in_failed))
                                Log.w(TAG, "Firebase sign-in failed (one-tap)", exception)
                                Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // no id token - silent / fallback if desired
                    setSyncStatus(getString(R.string.sign_in_failed))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "One Tap sign-in handling failed", t)
                setSyncStatus(getString(R.string.sign_in_failed))
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

        // The licence rows, the account id and the sync status all live in activity_user.xml now.
        // They used to be TextViews built here and spliced in next to the badge chip, which meant
        // the account page's most load-bearing information had no presence in the layout at all.
        tvSyncStatus = findViewById(R.id.sync_status)

        findViewById<View>(R.id.account_uid_row).setOnClickListener { copyAccountIdToClipboard() }

        btnSignOut = findViewById(R.id.signout_button)

        // achievements list setup
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view_achievements)
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        setupRecyclerView()

        findViewById<ConstraintLayout>(R.id.view_user).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.user_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }

        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        setupTitleController(titleSurface)
        setupStats()
        rateSetup()
        shareSetup()

        // PRO badge text
        updateAccountCard()

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

        findViewById<MaterialButton>(R.id.login_button).setOnClickListener {
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

    /**
     * Licences can change while this activity is in the background - a purchase made on the PRO
     * tab, or an Ultimate granted by a sync somewhere else - so the card is rebuilt on the way in.
     */
    override fun onResume() {
        super.onResume()
        updateAccountCard()
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
        val upState = titleBar.titleView
        downState?.text = name
        upState.text = name
    }

    private fun updateUi() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val displayName = user.displayName ?: user.email ?: "User Page"
            setUserTitleViews(displayName)
            btnSignOut.visibility = View.VISIBLE
            findViewById<MaterialButton>(R.id.login_button).visibility = View.GONE
            val photo = user.photoUrl
            if (photo != null) {
                userImg.imageTintList = null
                com.bumptech.glide.Glide.with(this).load(photo).circleCrop().into(userImg)
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
        setSyncStatus(null)
        btnSignOut.visibility = View.GONE
        userImg.setImageResource(R.drawable.ic_account)
        userImg.imageTintList = getColorStateListFromAttr(R.attr.colorOnSurfaceVariant)
        setUserTitleViews("User Page")
        updateAccountCard()
    }

    /** Show a sync status line, or hide it when there is nothing to say. */
    private fun setSyncStatus(text: String?) {
        tvSyncStatus.text = text ?: ""
        tvSyncStatus.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Called after Firebase sign-in is established (or when UI is refreshed while signed in).
     * Reads FirebaseAuth.currentUser for uid/photo/name and performs progress sync.
     *
     * Progress and notes sync are a paid feature, but this does not return early for free
     * accounts: [AutoSync] reads the licence keys for anyone signed in, which is the only way a
     * user who bought Ultimate on the PC can find out that they are PRO+ here. It reports
     * [AutoSync.Outcome.SKIPPED] if that read leaves them on the free tier.
     */
    private fun onSigninSuccess() {
        val uid = AuthManager.getUid()
        if (uid == null) {
            setSyncStatus(getString(R.string.sign_in_failed))
            return
        }
        findViewById<MaterialButton>(R.id.login_button).visibility = View.GONE

        setSyncStatus(getString(R.string.syncing_progress))
        // Routed through AutoSync so this shares the single-flight guard with the sync the
        // process lifecycle fires - two overlapping read-merge-writes against one document would
        // each miss the other's result.
        AutoSync.syncNow(this, force = true) { outcome ->
            runOnUiThread {
                when (outcome) {
                    AutoSync.Outcome.SUCCESS -> setSyncStatus(getString(R.string.sync_complete))
                    AutoSync.Outcome.FAILED -> setSyncStatus(getString(R.string.sync_failed))
                    AutoSync.Outcome.SKIPPED ->
                        // Either a sync is already in flight, or this is a free account and the
                        // licence read found nothing to grant.
                        if (!EntitlementManager.hasAnyPro(this)) {
                            setSyncStatus(getString(R.string.sync_available_pro_only))
                        }
                }
                // Always refresh the achievements/statistics view after a sync attempt
                setupRecyclerView()
                setupStats()
                // A sync can grant Ultimate from another platform, so the badges may have changed.
                updateAccountCard()
            }
        }
    }

    /**
     * The licences card and the account card.
     *
     * A port of the Windows account page's ApplyLicences. PRO and PRO+ are purchases from a
     * specific store, so each platform gets a row: a user with PRO+ on their PC and nothing here,
     * and one with PRO+ here, are the same word without the platform beside it.
     */
    private fun updateAccountCard() {
        val ultimate = EntitlementManager.hasUltimate(this)
        val androidTier = EntitlementManager.currentTier(this)
        val windowsWire = WindowsTierPreference(this).getValue()
        val windowsTier = EntitlementRules.fromWire(windowsWire)

        // An Ultimate owner sees "PRO+ Ultimate" on both rows - that is what they bought, and the
        // PRO+ it grants on each is a consequence rather than a second purchase.
        findViewById<TextView>(R.id.license_android_value)?.text =
            if (ultimate) EntitlementRules.ULTIMATE_PRODUCT_NAME
            else EntitlementRules.tierName(androidTier) ?: getString(R.string.free)

        // Until a sync has read one, the PC's tier is not known rather than free: a device that
        // has not synced yet is not a device that owns nothing.
        findViewById<TextView>(R.id.license_windows_value)?.text = when {
            ultimate -> EntitlementRules.ULTIMATE_PRODUCT_NAME
            windowsWire.isEmpty() -> getString(R.string.account_tier_unknown)
            else -> EntitlementRules.tierName(windowsTier) ?: getString(R.string.free)
        }

        // The Ultimate row and the offer are the two halves of one state: you own it, or you are
        // being sold it.
        findViewById<View>(R.id.license_ultimate_row)?.visibility =
            if (ultimate) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.license_ultimate_value)?.text =
            getString(R.string.ultimate_owned)

        findViewById<View>(R.id.ultimate_offer)?.visibility =
            if (ultimate) View.GONE else View.VISIBLE
        findViewById<MaterialButton>(R.id.ultimate_offer_btn)?.apply {
            // The upgrade when PRO+ is already owned on either platform, the full product
            // otherwise - the two products the PRO page sells.
            setText(
                if (androidTier == Tier.PRO_PLUS || windowsTier == Tier.PRO_PLUS)
                    R.string.ultimate_upgrade_title
                else R.string.ultimate_buy
            )
            setOnClickListener { goToProPage() }
        }

        findViewById<TextView>(R.id.account_uid_value)?.text =
            AuthManager.getUid() ?: getString(R.string.account_id_signed_out)

        val lastSync = LastSyncPreference(this).getValue()
        findViewById<TextView>(R.id.last_synced_value)?.text =
            if (lastSync <= 0L) {
                getString(R.string.last_synced_never)
            } else {
                DateUtils.getRelativeTimeSpanString(
                    lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString()
            }
    }

    /**
     * The Firebase uid, which is what ties this device to the PC.
     *
     * Worth copying rather than only reading: it is the one value that makes a sync problem
     * diagnosable, and it is 28 characters of base64 that nobody should be retyping.
     */
    private fun copyAccountIdToClipboard() {
        val uid = AuthManager.getUid() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.account_id), uid))
        Toast.makeText(this, R.string.account_id_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setupTitleController(titleSurface: View) {
        findViewById<ScrollView>(R.id.user_scroll).viewTreeObserver.addOnScrollChangedListener {
            val scrollY = findViewById<ScrollView>(R.id.user_scroll).scrollY
            if (scrollY > 150) {
                titleSurface.visibility = View.VISIBLE
                titleBar.titleView.visibility = View.VISIBLE
                findViewById<TextView>(R.id.user_title_downstate).visibility = View.INVISIBLE
                titleBar.container.elevation = resources.getDimension(R.dimen.one_elevation)
            } else {
                titleSurface.visibility = View.INVISIBLE
                titleBar.titleView.visibility = View.INVISIBLE
                findViewById<TextView>(R.id.user_title_downstate).visibility = View.VISIBLE
                titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)
            }
        }
    }

    private fun setupBackButton() {
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
        val params = titleBar.container.layoutParams as ViewGroup.LayoutParams
        params.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = params

        val params2 = findViewById<MaterialCardView>(R.id.user_img_container).layoutParams as ViewGroup.MarginLayoutParams
        params2.topMargin = top
        findViewById<MaterialCardView>(R.id.user_img_container).layoutParams = params2
    }

    override fun achievementClickListener(item: Achievement, position: Int) {
        // Not implemented yet
    }
}