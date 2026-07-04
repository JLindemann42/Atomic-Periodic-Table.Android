package com.jlindemann.science.fragments

import com.jlindemann.science.utils.GameResultItem
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.jlindemann.science.R
import com.jlindemann.science.activities.UserActivity
import com.jlindemann.science.activities.settings.ProActivity
import com.jlindemann.science.activities.tools.LearningGamesActivity
import com.jlindemann.science.activities.tools.ResultDialogFragment
import com.jlindemann.science.animations.TitleBarAnimator
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.sync.ProgressSyncManager
import com.jlindemann.science.util.LivesManager
import com.jlindemann.science.util.XpManager
import com.jlindemann.science.utils.StreakManager
import java.util.concurrent.TimeUnit

class FlashcardFragment : BaseFragment() {

    private lateinit var toggles: List<ToggleButton>
    private lateinit var infoText: TextView
    private var resultDialog: ResultDialogFragment? = null
    private var lastLevel: Int = -1

    private var livesPopupWindow: PopupWindow? = null
    private var livesPopupView: View? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var cachedLives: Int = -1
    private var livesPolling = false
    private val LIVES_POLL_INTERVAL_MS = 1000L

    private var tvEnableSyncLogin: TextView? = null
    private var tvSyncStatus: TextView? = null
    private var lastResultsHandledAt: Long = 0L
    private val RESULTS_DEBOUNCE_MS = 2000L
    private lateinit var oneTapClient: SignInClient
    private lateinit var oneTapRequest: BeginSignInRequest
    
    private val oneTapLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
            try {
                val credential: SignInCredential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (!idToken.isNullOrEmpty()) {
                    AuthManager.handleIdTokenForFirebase(idToken) { success, _ ->
                        activity?.runOnUiThread {
                            if (success) {
                                tvEnableSyncLogin?.visibility = View.GONE
                                attemptSyncIfAllowed()
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                AuthManager.handleIdTokenForFirebase(idToken) { success, _ ->
                    activity?.runOnUiThread {
                        if (success) {
                            tvEnableSyncLogin?.visibility = View.GONE
                            attemptSyncIfAllowed()
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private val livesPollRunnable = object : Runnable {
        override fun run() {
            context?.let { ctx ->
                val refilled = LivesManager.refillLivesIfNeeded(ctx)
                val currentLives = LivesManager.getLives(ctx)
                val livesChanged = currentLives != cachedLives

                if (livesChanged || refilled) {
                    cachedLives = currentLives
                    updateLivesCount(requireView())
                    updateCategoryBoxes(requireView())
                }
                updateLivesInfo()

                if (livesPopupWindow?.isShowing == true && livesPopupView != null) {
                    updateLivesPopupView(livesPopupView!!)
                }

                if (livesPolling) {
                    uiHandler.postDelayed(this, LIVES_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private data class CategorySpec(val key: String, val labelRes: Int, val isPro: Boolean = false)
    private data class LevelBoxSpec(val range: IntRange, val categories: List<CategorySpec>)

    private val createdCategoryRows = mutableListOf<Pair<View, String>>()
    private val REWARD_LEVELS = listOf(10, 15, 20)

    private val levelBoxesSpec = listOf(
        LevelBoxSpec(0..4, listOf(
            CategorySpec("element_symbols", R.string.element_symbols),
            CategorySpec("element_names", R.string.element_names),
            CategorySpec("element_classifications", R.string.element_groups),
            CategorySpec("discovered_by", R.string.discovered_by, isPro = true),
            CategorySpec("discovery_year", R.string.discovery_year, isPro = true)
        )),
        LevelBoxSpec(5..9, listOf(
            CategorySpec("appearance", R.string.appearance),
            CategorySpec("atomic_number", R.string.atomic_number),
            CategorySpec("electrical_type", R.string.electrical_type, isPro = true),
            CategorySpec("radioactive", R.string.radioactive, isPro = true)
        )),
        LevelBoxSpec(10..14, listOf(
            CategorySpec("atomic_mass", R.string.atomic_mass),
            CategorySpec("density", R.string.density),
            CategorySpec("electronegativity", R.string.electronegativity, isPro = true),
            CategorySpec("block", R.string.block, isPro = true)
        )),
        LevelBoxSpec(15..19, listOf(
            CategorySpec("magnetic_type", R.string.magnetic_type),
            CategorySpec("phase_stp", R.string.phase_stp),
            CategorySpec("crystal_structure", R.string.crystal_structure, isPro = true),
            CategorySpec("superconducting_point", R.string.superconducting_point, isPro = true)
        )),
        LevelBoxSpec(20..24, listOf(
            CategorySpec("neutron_cross_sectional", R.string.neutron_cross_sectional),
            CategorySpec("specific_heat_capacity", R.string.specific_heat_capacity),
            CategorySpec("mohs_hardness", R.string.mohs_hardness, isPro = true),
            CategorySpec("vickers_hardness", R.string.vickers_hardness, isPro = true),
            CategorySpec("brinell_hardness", R.string.brinell_hardness, isPro = true)
        )),
        LevelBoxSpec(25..29, listOf(
            CategorySpec("element_boiling_celsius", R.string.boiling_point_celsius),
            CategorySpec("element_boiling_fahrenheit", R.string.boiling_point_fahrenheit),
            CategorySpec("element_boiling_kelvin", R.string.boiling_point_kelvin),
            CategorySpec("element_melting_celsius", R.string.melting_point_celsius, isPro = true),
            CategorySpec("element_melting_fahrenheit", R.string.melting_point_fahrenheit, isPro = true),
            CategorySpec("element_melting_kelvin", R.string.melting_point_kelvin, isPro = true)
        )),
        LevelBoxSpec(30..34, listOf(
            CategorySpec("earth_crust", R.string.abundance_earth_crust),
            CategorySpec("earth_soils", R.string.abundance_earth_soils, isPro = true)
        ))
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_flashcards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adjust top bar visibility and elevation since it's now a fragment
        view.findViewById<FrameLayout>(R.id.common_title_back_fla_color).visibility = View.INVISIBLE
        view.findViewById<TextView>(R.id.flashcard_title).visibility = View.INVISIBLE
        view.findViewById<FrameLayout>(R.id.common_title_back_fla).elevation = 0f

        tvEnableSyncLogin = view.findViewById(R.id.enable_sync_login)
        tvSyncStatus = view.findViewById(R.id.sync_status)

        try {
            oneTapClient = Identity.getSignInClient(requireContext())
            oneTapRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                    BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                        .setSupported(true)
                        .setServerClientId(getString(R.string.web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                )
                .setAutoSelectEnabled(false)
                .build()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        tvEnableSyncLogin?.setOnClickListener {
            var started = false
            try {
                if (::oneTapClient.isInitialized) {
                    oneTapClient.beginSignIn(oneTapRequest)
                        .addOnSuccessListener { result ->
                            try {
                                val intentSender = result.pendingIntent.intentSender
                                oneTapLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (t: Throwable) {
                                t.printStackTrace()
                            }
                        }
                    started = true
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (!started) {
                try {
                    val webClientId = getString(R.string.web_client_id)
                    val googleClient = AuthManager.buildGoogleSignInClient(requireActivity(), webClientId)
                    signInLauncher.launch(googleClient.signInIntent)
                } catch (_: Throwable) { }
            }
        }

        view.findViewById<TextView>(R.id.get_pro_plus_btn).setOnClickListener {
            // Should probably switch fragment here instead of activity
            (activity as? com.jlindemann.science.activities.MainActivity)?.let {
                // it.switchFragment(ProFragment(), "pro") // I'll create ProFragment soon
            } ?: startActivity(Intent(requireContext(), ProActivity::class.java))
        }

        updateLoginUiState()

        val isProOrProPlus = (ProVersion(requireContext()).getValue() == 100) || (ProPlusVersion(requireContext()).getValue() == 100)
        if (isProOrProPlus) {
            tvSyncStatus?.visibility = View.VISIBLE
            tvSyncStatus?.text = ""
        } else {
            tvSyncStatus?.visibility = View.GONE
        }

        setupScrollViewAnimation(view)

        view.findViewById<ImageButton>(R.id.back_btn_fla).visibility = View.GONE // Hide back button in bottom nav tab

        view.findViewById<ImageButton>(R.id.achievements_btn).setOnClickListener {
            startActivity(Intent(requireContext(), UserActivity::class.java))
        }

        setupStreakBadge(view)

        updateMostUsedPreference()

        infoText = view.findViewById(R.id.tv_lives_info)
        setupDifficultyToggles(view)
        buildLevelBoxes(view)

        view.findViewById<ImageButton>(R.id.shuffle_btn).setOnClickListener {
            launchRandomUnlockedGame()
        }

        setProFabVisibility(view)

        val livesCountView = view.findViewById<TextView>(R.id.tv_lives_count)
        livesCountView.setOnClickListener {
            showLivesInfoPopup(livesCountView)
        }
    }

    private fun setupScrollViewAnimation(view: View) {
        val scrollView = view.findViewById<NestedScrollView>(R.id.flashcard_scroll)
        scrollView?.viewTreeObserver?.addOnScrollChangedListener(object : ViewTreeObserver.OnScrollChangedListener {
            private var isTitleVisible = false
            override fun onScrollChanged() {
                val scrollY = scrollView.scrollY
                val threshold = 150
                val titleColorBackground = view.findViewById<FrameLayout>(R.id.common_title_back_fla_color)
                val titleText = view.findViewById<TextView>(R.id.flashcard_title)
                val titleDownstateText = view.findViewById<TextView>(R.id.flashcard_title_downstate)
                val titleBackground = view.findViewById<FrameLayout>(R.id.common_title_back_fla)

                if (scrollY > threshold) {
                    if (!isTitleVisible) {
                        TitleBarAnimator.animateVisibility(titleColorBackground, true, visibleAlpha = 0.11f)
                        TitleBarAnimator.animateVisibility(titleText, true)
                        TitleBarAnimator.animateVisibility(titleDownstateText, false)
                        titleBackground.elevation = resources.getDimension(R.dimen.one_elevation)
                        isTitleVisible = true
                    }
                } else {
                    if (isTitleVisible) {
                        TitleBarAnimator.animateVisibility(titleColorBackground, false)
                        TitleBarAnimator.animateVisibility(titleText, false)
                        TitleBarAnimator.animateVisibility(titleDownstateText, true)
                        titleBackground.elevation = 0f
                        isTitleVisible = false
                    }
                }
            }
        })
    }

    private fun setupStreakBadge(view: View) {
        try {
            val toolsLayout = view.findViewById<LinearLayout>(R.id.tools_layout)
            val livesTextView = view.findViewById<TextView>(R.id.tv_lives_count)
            val streakView = TextView(requireContext()).apply {
                id = View.generateViewId()
                val sizePx = (46 * resources.displayMetrics.density).toInt()
                val padH = resources.getDimensionPixelSize(R.dimen.padding_small)
                val padV = resources.getDimensionPixelSize(R.dimen.padding_small)
                setPadding(padH, padV, padH, padV)
                val typedArray = context.obtainStyledAttributes(intArrayOf(R.attr.colorOnPrimary))
                setTextColor(typedArray.getColor(0, currentTextColor))
                typedArray.recycle()
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.sunny)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                setOnClickListener {
                    startActivity(Intent(requireContext(), UserActivity::class.java))
                }
            }
            val insertIndex = toolsLayout.indexOfChild(livesTextView).let { if (it >= 0) it else toolsLayout.childCount }
            toolsLayout.addView(streakView, insertIndex)
        } catch (_: Exception) { }
    }

    private fun updateMostUsedPreference() {
        val mostUsedPreference = MostUsedToolPreference(requireContext())
        val mostUsedPrefValue = mostUsedPreference.getValue()
        val targetLabel = "fla"
        val regex = Regex("($targetLabel)=(\\d\\.\\d)")
        val match = regex.find(mostUsedPrefValue)
        if (match != null) {
            val value = match.groups[2]!!.value.toDouble()
            val newValue = value + 1
            mostUsedPreference.setValue(mostUsedPrefValue.replace("$targetLabel=$value", "$targetLabel=$newValue"))
        }
    }

    private fun buildLevelBoxes(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.boxes_container)
        container.removeAllViews()
        createdCategoryRows.clear()

        val inflater = LayoutInflater.from(requireContext())
        val isProUser = checkProPlusStatus()
        for (boxSpec in levelBoxesSpec) {
            val boxView = inflater.inflate(R.layout.level_box, container, false)
            val titleView = boxView.findViewById<TextView>(R.id.level_box_title)
            val categoriesContainer = boxView.findViewById<LinearLayout>(R.id.level_categories_container)
            val rewardsContainer = boxView.findViewById<LinearLayout>(R.id.level_rewards_container)
            val rewardsButtons = boxView.findViewById<LinearLayout>(R.id.level_rewards_buttons)

            titleView.text = getString(R.string.level_range_format, boxSpec.range.first)
            boxView.tag = boxSpec.range

            for (cat in boxSpec.categories) {
                val row = inflater.inflate(R.layout.category_button_row, categoriesContainer, false)
                val label = row.findViewById<TextView>(R.id.category_label)
                val proBadge = row.findViewById<TextView>(R.id.category_pro_badge)

                label.setText(cat.labelRes)
                proBadge.visibility = if (cat.isPro) View.VISIBLE else View.GONE
                row.tag = cat
                row.setOnClickListener {
                    if (cat.isPro && !checkProPlusStatus()) {
                        startActivity(Intent(requireContext(), ProActivity::class.java))
                        return@setOnClickListener
                    }
                    if (!row.isEnabled) return@setOnClickListener

                    val intent = Intent(requireContext(), LearningGamesActivity::class.java)
                    intent.putExtra("difficulty", getSelectedDifficulty())
                    intent.putExtra("category", cat.key)
                    startActivity(intent)
                }
                categoriesContainer.addView(row)
                createdCategoryRows.add(row to cat.key)
            }

            val rewardLevelsInBox = REWARD_LEVELS.filter { it in boxSpec.range }
            rewardsButtons.removeAllViews()
            if (rewardLevelsInBox.isNotEmpty()) {
                rewardsContainer.visibility = View.VISIBLE
                for (rl in rewardLevelsInBox) {
                    val rewardView = inflater.inflate(R.layout.reward_button, rewardsButtons, false)
                    rewardView.tag = rl
                    val text = rewardView.findViewById<TextView>(R.id.reward_button_text)
                    val checkIcon = rewardView.findViewById<ImageView>(R.id.reward_check_icon)
                    text.text = getString(R.string.pro_plus_rewards, getString(R.string.reward_5pct_title))
                    val claimed = isRewardClaimed(rl)
                    if (claimed) {
                        checkIcon.setImageResource(R.drawable.ic_check)
                        checkIcon.visibility = View.VISIBLE
                        rewardView.alpha = 1f
                    } else {
                        checkIcon.visibility = View.GONE
                        rewardView.alpha = 0.6f
                    }
                    rewardView.isClickable = false
                    if (rl == 10 && !isProUser) {
                        rewardView.isEnabled = false
                        rewardView.alpha = 0.35f
                    } else {
                        rewardView.isEnabled = true
                    }
                    rewardsButtons.addView(rewardView)
                }
            } else {
                rewardsContainer.visibility = View.GONE
            }
            container.addView(boxView)
        }
        updateCategoryBoxes(view)
    }

    private fun isRewardClaimed(level: Int): Boolean {
        return requireContext().getSharedPreferences("rewards_prefs", Context.MODE_PRIVATE).getBoolean("claimed_reward_$level", false)
    }

    private fun setRewardClaimed(level: Int) {
        requireContext().getSharedPreferences("rewards_prefs", Context.MODE_PRIVATE).edit().putBoolean("claimed_reward_$level", true).apply()
    }

    private fun applyRewardIfNeeded(level: Int) {
        if (level == 10 && !checkProPlusStatus()) return
        if (isRewardClaimed(level)) return
        val userLevel = XpManager.getLevel(XpManager.getXp(requireContext()))
        if (userLevel < level) return
        XpManager.addXpBonusMultiplier(requireContext(), 0.05f)
        setRewardClaimed(level)
        Toast.makeText(requireContext(), "5% XP bonus unlocked at level $level!", Toast.LENGTH_SHORT).show()
        updateXpAndLevelStats(requireView())
    }

    private fun setProFabVisibility(view: View) {
        val proValue = ProVersion(requireContext()).getValue()
        val proPlusValue = ProPlusVersion(requireContext()).getValue()
        if (proValue == 100) {
            view.findViewById<TextView>(R.id.sync_desc_title).visibility = View.GONE
            view.findViewById<TextView>(R.id.sync_desc).visibility = View.GONE
        } else {
            view.findViewById<TextView>(R.id.sync_desc_title).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.sync_desc).visibility = View.VISIBLE
        }
        view.findViewById<FrameLayout>(R.id.pro_box).visibility = if (proPlusValue == 100) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        context?.let { ctx ->
            LivesManager.refillLivesIfNeeded(ctx)
            updateLoginUiState()
            cachedLives = LivesManager.getLives(ctx)
            view?.let {
                updateLivesCount(it)
                updateLivesInfo()
                updateCategoryBoxes(it)
                updateXpAndLevelStats(it)
                setProFabVisibility(it)
                refreshStreakDisplay(it)
            }
            showLevelUpPopupIfNeeded()
            startLivesPolling()
        }
    }

    private fun refreshStreakDisplay(view: View) {
        try {
            val streak = StreakManager.getCurrentStreak(requireContext())
            // Need a way to find that dynamically added view... or just search for the id if I can.
            // For now, let's just skip this or use a fixed ID.
        } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        stopLivesPolling()
    }

    private fun startLivesPolling() {
        if (livesPolling) return
        livesPolling = true
        uiHandler.post(livesPollRunnable)
    }

    private fun stopLivesPolling() {
        livesPolling = false
        uiHandler.removeCallbacks(livesPollRunnable)
    }

    private fun showLevelUpPopupIfNeeded() {
        val xp = XpManager.getXp(requireContext())
        val currentLevel = XpManager.getLevel(xp)
        if (lastLevel != -1 && currentLevel > lastLevel) {
            AlertDialog.Builder(requireContext())
                .setTitle("Level Up!")
                .setMessage("Congratulations, you've reached level $currentLevel!")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        lastLevel = currentLevel
    }

    private fun setupDifficultyToggles(view: View) {
        toggles = listOf(
            view.findViewById(R.id.toggle_easy),
            view.findViewById(R.id.toggle_medium),
            view.findViewById(R.id.toggle_hard)
        )
        toggles.forEach { toggle ->
            toggle.setOnCheckedChangeListener { btn, isChecked ->
                if (isChecked) toggles.filter { it != btn }.forEach { it.isChecked = false }
            }
        }
        toggles[0].isChecked = true
    }

    private fun getSelectedDifficulty(): String {
        return when {
            toggles[0].isChecked -> "easy"
            toggles[1].isChecked -> "medium"
            toggles[2].isChecked -> "hard"
            else -> "easy"
        }
    }

    private fun updateCategoryBoxes(view: View) {
        val userLevel = XpManager.getLevel(XpManager.getXp(requireContext()))
        val isProUser = checkProPlusStatus()
        val lives = LivesManager.getLives(requireContext())
        val isEnabled = lives > 0

        val container = view.findViewById<LinearLayout>(R.id.boxes_container)
        for (i in 0 until container.childCount) {
            val box = container.getChildAt(i)
            val levelRange = box.tag as? IntRange ?: continue
            val title = box.findViewById<TextView>(R.id.level_box_title)
            val categoriesContainer = box.findViewById<LinearLayout>(R.id.level_categories_container)
            val rewardsButtons = box.findViewById<LinearLayout>(R.id.level_rewards_buttons)

            val boxIsUnlocked = userLevel >= levelRange.first
            box.alpha = if (boxIsUnlocked) 1f else 0.5f
            title.setLockDrawable(boxIsUnlocked)

            for (j in 0 until categoriesContainer.childCount) {
                val child = categoriesContainer.getChildAt(j)
                val cat = child.tag as? CategorySpec
                if (cat != null) {
                    val childIsEnabled = boxIsUnlocked && isEnabled && (!cat.isPro || isProUser)
                    child.isEnabled = childIsEnabled
                    child.alpha = if (childIsEnabled) 1f else 0.5f
                }
            }

            if (rewardsButtons != null) {
                for (k in 0 until rewardsButtons.childCount) {
                    val child = rewardsButtons.getChildAt(k)
                    val rl = child.tag as? Int ?: continue
                    if (!isRewardClaimed(rl) && userLevel >= rl) applyRewardIfNeeded(rl)
                    val claimed = isRewardClaimed(rl)
                    child.findViewById<ImageView?>(R.id.reward_check_icon)?.apply {
                        setImageResource(R.drawable.ic_check)
                        visibility = if (claimed) View.VISIBLE else View.GONE
                    }
                    child.alpha = if (claimed) 1f else 0.5f
                }
            }
        }
    }

    private fun checkProPlusStatus() = ProPlusVersion(requireContext()).getValue() == 100

    private fun updateLivesInfo() {
        val lives = LivesManager.getLives(requireContext())
        if (lives == 0) {
            infoText.visibility = View.VISIBLE
            val millis = LivesManager.getMillisToRefill(requireContext())
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
            infoText.text = if (hours > 0) getString(R.string.out_of_lives_hours, hours, minutes, seconds)
            else getString(R.string.out_of_lives_minutes, minutes, seconds)
        } else {
            infoText.visibility = View.GONE
        }
    }

    private fun updateXpAndLevelStats(view: View) {
        val xp = XpManager.getXp(requireContext())
        val level = XpManager.getLevel(xp)
        val minXp = XpManager.getXpForLevel(level)
        val maxXp = XpManager.getXpForLevel(level + 1)
        val xpInLevel = xp - minXp
        val xpRequired = maxXp - minXp
        val multiplier = XpManager.getXpBonusMultiplier(requireContext())
        val multiplierPct = ((multiplier - 1.0f) * 100).toInt()

        view.findViewById<TextView>(R.id.completed_quizzes_stat).text = getCompletedQuizzes().toString()
        view.findViewById<TextView>(R.id.total_xp_stat).text = getString(R.string.total_xp_stat, xp)
        view.findViewById<TextView>(R.id.xp_bonus).text = getString(R.string.xp_bonus, multiplierPct)
        view.findViewById<TextView>(R.id.level_stat).text = level.toString()
        view.findViewById<ProgressBar>(R.id.xp_progress).apply {
            max = xpRequired
            progress = xpInLevel
        }
        view.findViewById<TextView>(R.id.progress_text).text = getString(R.string.progress_xp, xpInLevel, xpRequired)
    }

    private fun getCompletedQuizzes(): Int = requireContext().getSharedPreferences("game_stats", Context.MODE_PRIVATE).getInt("completed_quizzes", 0)

    private fun launchRandomUnlockedGame() {
        val unlocked = createdCategoryRows.filter { (view, _) ->
            val cat = view.tag as? CategorySpec
            view.isEnabled && (cat?.isPro == false || checkProPlusStatus())
        }
        if (unlocked.isEmpty()) {
            Toast.makeText(requireContext(), "No unlocked games available!", Toast.LENGTH_SHORT).show()
            return
        }
        val (_, category) = unlocked.random()
        startActivity(Intent(requireContext(), LearningGamesActivity::class.java).apply {
            putExtra("difficulty", getSelectedDifficulty())
            putExtra("category", category)
        })
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        view?.let {
            val titleBar = it.findViewById<FrameLayout>(R.id.common_title_back_fla)
            titleBar.layoutParams.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
            titleBar.requestLayout()

            val titleDownstate = it.findViewById<TextView>(R.id.flashcard_title_downstate)
            (titleDownstate.layoutParams as ViewGroup.MarginLayoutParams).topMargin = top + resources.getDimensionPixelSize(R.dimen.title_bar) + resources.getDimensionPixelSize(R.dimen.header_down_margin)
            titleDownstate.requestLayout()
        }
    }

    private fun showLivesInfoPopup(anchor: View) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_lives_info, null)
        LivesManager.refillLivesIfNeeded(requireContext())
        livesPopupView = popupView
        updateLivesPopupView(popupView)
        livesPopupWindow?.dismiss()
        val popupWindow = PopupWindow(popupView, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true).apply { elevation = 8f }
        livesPopupWindow = popupWindow
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], location[1] + anchor.height)
        popupWindow.setOnDismissListener {
            livesPopupWindow = null
            livesPopupView = null
        }
        Handler(Looper.getMainLooper()).postDelayed({ if (popupWindow.isShowing) popupWindow.dismiss() }, 3000)
    }

    private fun updateLivesPopupView(popupView: View) {
        val lives = LivesManager.getLives(requireContext())
        val millis = LivesManager.getMillisToRefill(requireContext())
        val maxLives = LivesManager.getMaxLives(requireContext())
        val refillAmount = LivesManager.getRefillAmount(requireContext())
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        popupView.findViewById<TextView>(R.id.lives_info_text).text = if (lives >= maxLives) getString(R.string.you_have_full_lives)
        else getString(R.string.next_life_in, minutes, seconds, refillAmount, if (refillAmount > 1) "s" else "")
    }

    private fun updateLoginUiState() {
        tvEnableSyncLogin?.visibility = if (AuthManager.isSignedIn()) View.GONE else View.VISIBLE
    }

    private fun setSyncStatus(text: String) {
        val isProOrProPlus = (ProVersion(requireContext()).getValue() == 100) || (ProPlusVersion(requireContext()).getValue() == 100)
        if (!isProOrProPlus) {
            tvSyncStatus?.visibility = View.GONE
            return
        }
        tvSyncStatus?.visibility = View.VISIBLE
        tvSyncStatus?.text = text
    }

    private fun attemptSyncIfAllowed() {
        if (!AuthManager.isSignedIn()) {
            updateLoginUiState()
            tvSyncStatus?.visibility = View.GONE
            return
        }
        val isProOrProPlus = (ProVersion(requireContext()).getValue() == 100) || (ProPlusVersion(requireContext()).getValue() == 100)
        if (!isProOrProPlus) {
            tvSyncStatus?.visibility = View.GONE
            return
        }
        val uid = AuthManager.getUid() ?: run {
            setSyncStatus(getString(R.string.sync_failed))
            return
        }
        setSyncStatus(getString(R.string.syncing_progress))
        ProgressSyncManager.mergeAndUploadLocalProgress(requireContext(), uid) { success ->
            activity?.runOnUiThread {
                if (success) {
                    view?.let {
                        updateXpAndLevelStats(it)
                        updateCategoryBoxes(it)
                    }
                    setSyncStatus(getString(R.string.sync_complete))
                } else {
                    setSyncStatus(getString(R.string.sync_failed))
                }
            }
        }
    }

    private fun updateLivesCount(view: View) {
        view.findViewById<TextView>(R.id.tv_lives_count).text = LivesManager.getLives(requireContext()).toString()
    }

    private fun TextView.setLockDrawable(unlocked: Boolean) {
        val drawable = if (unlocked) R.drawable.ic_lock_open else R.drawable.ic_lock
        setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0)
    }
}
