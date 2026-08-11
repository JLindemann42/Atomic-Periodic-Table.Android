package com.jlindemann.science.activities.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.adapter.FavoriteEquation
import com.jlindemann.science.adapter.FavoriteEquationsAdapter
import com.jlindemann.science.ai.compose.DeepLinkNavigator
import com.jlindemann.science.ai.data.ElementTally
import com.jlindemann.science.ai.data.EquationBalancer
import com.jlindemann.science.ai.data.Term
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.MostUsedToolPreference
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.ThemePreference
import com.jlindemann.science.utils.ToastUtil
import com.jlindemann.science.utils.UnifiedTitleBarController

/**
 * Balances a chemical equation as the user types it.
 *
 * Shares its solver with the assistant: both call [EquationBalancer], so the answer to
 * "balance Fe + O2 -> Fe2O3" is the same whether it is asked in the chat or typed here. That
 * sharing is the point of the screen. The app's older [ChemicalReactionsActivity] carries its own
 * solver, and that one is broken — it augments the system with a zero right-hand side and then
 * reads the solution out of that same zero column, so every input comes back as zeros or NaN.
 *
 * The failure messages are the assistant's, for the same reason. An equation that cannot balance
 * with positive integers, or that has several independent balances, is told to the user rather
 * than answered with one of the possibilities — and those sentences are already translated into
 * every language the app ships.
 */
class EquationBalancerActivity : BaseActivity() {

    private lateinit var titleBar: UnifiedTitleBarController

    /** The last balanced equation; null whenever the result line is a prompt or a failure. */
    private var balancedEquation: String? = null

    /** What was typed to produce [balancedEquation]; saved beside it so a favorite can be edited. */
    private var balancedInput: String? = null

    private lateinit var favoritesPreferences: SharedPreferences
    private lateinit var favoritesAdapter: FavoriteEquationsAdapter

    /** PRO or PRO+; favorites are a paid feature here as they are on the molar mass calculator. */
    private var hasProAccess: Boolean = false

    /** Every element symbol the app knows, for deciding what is a formula and what is prose. */
    private val symbols: Set<String> by lazy {
        ArrayList<Element>().also { ElementModel.getList(it) }.map { it.short }.toSet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themePrefValue = ThemePreference(this).getValue()
        when {
            themePrefValue == 100 -> {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_NO -> setTheme(R.style.AppTheme)
                    Configuration.UI_MODE_NIGHT_YES -> setTheme(R.style.AppThemeDark)
                }
            }
            themePrefValue == 0 -> setTheme(R.style.AppTheme)
            themePrefValue == 1 -> setTheme(R.style.AppThemeDark)
        }

        setContentView(R.layout.activity_equation_balancer)
        findViewById<ConstraintLayout>(R.id.view_balancer).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        titleBar = UnifiedTitleBarController(findViewById(R.id.unified_titlebar_include))
        titleBar.setTitle(R.string.balancer_tool_title)
        titleBar.hideAction()
        titleBar.hideCategories()
        titleBar.searchRow.visibility = View.GONE
        titleBar.backButton.setOnClickListener { onBackPressed() }

        val titleSurface = titleBar.container.findViewById<View>(R.id.unified_titlebar_surface)
        titleSurface.visibility = View.INVISIBLE
        titleBar.titleView.visibility = View.INVISIBLE
        titleBar.container.elevation = resources.getDimension(R.dimen.zero_elevation)

        val scroll = findViewById<ScrollView>(R.id.balancer_scroll)
        scroll.viewTreeObserver.addOnScrollChangedListener {
            val raised = scroll.scrollY > 150
            titleSurface.visibility = if (raised) View.VISIBLE else View.INVISIBLE
            titleBar.titleView.visibility = if (raised) View.VISIBLE else View.INVISIBLE
            findViewById<TextView>(R.id.balancer_title_downstate).visibility =
                if (raised) View.INVISIBLE else View.VISIBLE
            titleBar.container.elevation =
                resources.getDimension(if (raised) R.dimen.one_elevation else R.dimen.zero_elevation)
        }

        bumpMostUsed()
        inputController()
        favoritesController()

        for (id in listOf(R.id.example_1, R.id.example_2, R.id.example_3)) {
            val chip = findViewById<TextView>(id)
            chip.setOnClickListener {
                findViewById<TextInputEditText>(R.id.edit_text_balancer).setText(chip.text)
            }
        }

        // An equation handed over by the assistant's "open in app" chip. Filling it in after the
        // watcher is attached is what makes the screen open already answered: the user tapped the
        // chip from a balanced equation, and an empty box would ask them to type it again.
        intent.getStringExtra(DeepLinkNavigator.EXTRA_EQUATION)
            ?.takeIf { it.isNotBlank() }
            ?.let { findViewById<TextInputEditText>(R.id.edit_text_balancer).setText(it) }
    }

    override fun onApplySystemInsets(top: Int, bottom: Int, left: Int, right: Int) {
        val barParams = titleBar.container.layoutParams as ViewGroup.LayoutParams
        barParams.height = top + resources.getDimensionPixelSize(R.dimen.title_bar)
        titleBar.container.layoutParams = barParams

        val downstate = findViewById<TextView>(R.id.balancer_title_downstate)
        val headlineParams = downstate.layoutParams as ViewGroup.MarginLayoutParams
        headlineParams.topMargin = top +
            resources.getDimensionPixelSize(R.dimen.title_bar) +
            resources.getDimensionPixelSize(R.dimen.header_down_margin)
        downstate.layoutParams = headlineParams
    }

    /**
     * The favorites card: the PRO gate, the list, and the save button.
     *
     * Without PRO the list is hidden behind the upsell and the save button is not shown at all,
     * which is how the molar mass calculator gates the same feature. The screen itself stays
     * usable — balancing is free, only keeping the result is not.
     */
    private fun favoritesController() {
        favoritesPreferences = getSharedPreferences(FAVORITES_PREFERENCES, Context.MODE_PRIVATE)
        hasProAccess = ProVersion(this).getValue() == 100 || ProPlusVersion(this).getValue() == 100

        val list = findViewById<RecyclerView>(R.id.balancer_fav_rec_list)
        val noPro = findViewById<TextView>(R.id.balancer_no_pro_text)
        val proButton = findViewById<View>(R.id.balancer_pro_button)
        val saveButton = findViewById<View>(R.id.balancer_fav_star_btn)

        list.visibility = if (hasProAccess) View.VISIBLE else View.GONE
        noPro.visibility = if (hasProAccess) View.GONE else View.VISIBLE
        proButton.visibility = if (hasProAccess) View.GONE else View.VISIBLE
        saveButton.visibility = if (hasProAccess) View.VISIBLE else View.GONE

        proButton.setOnClickListener { goToProPage() }

        favoritesAdapter = FavoriteEquationsAdapter(
            onRemove = { input -> removeFavorite(input) },
            onCopy = { balanced -> copyToClipboard(balanced) },
            onRestore = { input ->
                findViewById<TextInputEditText>(R.id.edit_text_balancer).setText(input)
            }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = favoritesAdapter

        loadFavorites()

        saveButton.setOnClickListener {
            val balanced = balancedEquation
            val input = balancedInput
            // Only a balanced equation is worth keeping. Saving the prompt or a failure sentence
            // would fill the list with rows that answer nothing.
            if (balanced == null || input.isNullOrBlank()) {
                ToastUtil.showToast(this, getString(R.string.balancer_tool_prompt))
                return@setOnClickListener
            }
            saveFavorite(input, balanced)
            loadFavorites()
        }
    }

    /** Re-saving an equation replaces its row instead of adding a second one for the same input. */
    private fun saveFavorite(input: String, balanced: String) {
        val favorites = storedFavorites()
        favorites.removeIf { it.substringBefore(FAVORITE_SEPARATOR) == input }
        favorites.add("$input$FAVORITE_SEPARATOR$balanced")
        favoritesPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
    }

    private fun removeFavorite(input: String) {
        val favorites = storedFavorites()
        favorites.removeIf { it.substringBefore(FAVORITE_SEPARATOR) == input }
        favoritesPreferences.edit().putStringSet(FAVORITES_KEY, favorites).apply()
        loadFavorites()
    }

    private fun loadFavorites() {
        // A set has no order of its own, so the rows are sorted by the typed equation. Without it
        // the list reshuffles itself every time something is added or removed.
        val favorites = storedFavorites()
            .map {
                FavoriteEquation(
                    input = it.substringBefore(FAVORITE_SEPARATOR),
                    balanced = it.substringAfter(FAVORITE_SEPARATOR, "")
                )
            }
            .sortedBy { it.input.lowercase() }

        favoritesAdapter.updateEquations(favorites)
        findViewById<TextView>(R.id.balancer_no_fav_text).visibility =
            if (hasProAccess && favorites.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun storedFavorites(): MutableSet<String> =
        favoritesPreferences.getStringSet(FAVORITES_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("equation", text))
        ToastUtil.showToast(this, getString(R.string.balancer_tool_copied))
    }

    private fun inputController() {
        findViewById<TextInputEditText>(R.id.edit_text_balancer)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable) = render(s.toString())
            })
    }

    /**
     * Balance what has been typed so far and show it.
     *
     * An empty box shows the prompt rather than an error: a user who has typed nothing has not
     * made a mistake. Everything else gets a verdict, including the ones that cannot balance —
     * silence there would read as the tool having failed rather than the equation.
     */
    private fun render(input: String) {
        val output = findViewById<TextView>(R.id.balancer_out_text)
        val tally = findViewById<TextView>(R.id.balancer_tally_text)
        val header = findViewById<TextView>(R.id.balancer_tally_header)

        balancedEquation = null
        balancedInput = null

        fun show(text: String, proof: String? = null) {
            output.text = text
            tally.text = proof.orEmpty()
            val visible = if (proof.isNullOrBlank()) View.GONE else View.VISIBLE
            tally.visibility = visible
            header.visibility = visible
        }

        if (input.isBlank()) {
            show(getString(R.string.balancer_tool_prompt))
            return
        }

        when (val parsed = EquationBalancer.parse(input) { it in symbols }) {
            // No arrow yet is the normal state while typing, and calling that a parse failure
            // would scold the user for every keystroke up to the "->".
            EquationBalancer.ParseOutcome.NoArrow ->
                show(getString(R.string.balancer_tool_prompt))

            // An arrow was written and the formulas beside it were not readable. This used to show
            // the prompt as well, which told somebody who had typed an equation to type one.
            EquationBalancer.ParseOutcome.NotAnEquation ->
                show(getString(R.string.ai_equation_parse_failed))

            EquationBalancer.ParseOutcome.TooManySpecies ->
                show(getString(R.string.ai_equation_too_large))

            is EquationBalancer.ParseOutcome.Sides ->
                when (val result = EquationBalancer.balance(parsed.reactants, parsed.products)) {
                    is EquationBalancer.Result.Balanced -> {
                        balancedEquation = equationOf(result.reactants, result.products)
                        balancedInput = input.trim()
                        show(balancedEquation!!, proofOf(result.tally))
                    }
                    is EquationBalancer.Result.Failed ->
                        show(messageFor(result.reason, result.detail))
                }
        }
    }

    private fun equationOf(reactants: List<Term>, products: List<Term>): String {
        fun side(terms: List<Term>) = terms.joinToString(" + ") { term ->
            // A coefficient of 1 is written, not printed: "1Fe" is what a bug looks like.
            if (term.coefficient == 1) term.formula else "${term.coefficient}${term.formula}"
        }
        return "${side(reactants)} → ${side(products)}"
    }

    /**
     * The per-element atom counts, as plain text.
     *
     * The row template is the assistant's, and the assistant's renderer turns `**Fe**` into bold.
     * This screen's result is an ordinary TextView, so it was printing the asterisks. Stripping
     * them keeps the one translated row shared between the two surfaces rather than forking it.
     */
    private fun proofOf(tally: List<ElementTally>): String =
        tally.joinToString("\n") {
            getString(R.string.ai_equation_tally_row, it.symbol, it.left, it.right)
                .replace("**", "")
        }

    /**
     * @param detail the offending element symbol, when the solver identified one. Naming it turns
     *   "an element appears on only one side" into a sentence the user can act on without hunting.
     */
    private fun messageFor(reason: EquationBalancer.Reason, detail: String?): String = when (reason) {
        EquationBalancer.Reason.ONE_SIDED_ELEMENT ->
            if (detail == null) getString(R.string.ai_equation_one_sided)
            else getString(R.string.ai_equation_one_sided_named, detail)
        EquationBalancer.Reason.UNDERDETERMINED -> getString(R.string.ai_equation_underdetermined)
        EquationBalancer.Reason.TOO_LARGE -> getString(R.string.ai_equation_too_large)
        EquationBalancer.Reason.PARSE_FAILED -> getString(R.string.ai_equation_parse_failed)
        EquationBalancer.Reason.NO_SOLUTION -> getString(R.string.ai_equation_no_solution)
    }

    /** Same counter the other tools bump, so the most-used row reflects real use. */
    private fun bumpMostUsed() {
        val preference = MostUsedToolPreference(this)
        val current = preference.getValue()
        val match = Regex("(bal)=(\\d+\\.\\d+)").find(current) ?: return
        val value = match.groups[2]!!.value.toDouble()
        preference.setValue(current.replace("bal=$value", "bal=${value + 1}"))
    }

    private companion object {
        const val FAVORITES_PREFERENCES = "EquationBalancerPrefs"
        const val FAVORITES_KEY = "balancer_favorites"

        /**
         * Splits the typed equation from its balanced form inside one stored entry.
         *
         * A unit separator rather than the ":" the other tools use: an entry is split on the first
         * occurrence, and ":" is a character a user can type — a state symbol or a stray colon
         * would cut the row in the wrong place.
         */
        const val FAVORITE_SEPARATOR = "\u001F"
    }
}
