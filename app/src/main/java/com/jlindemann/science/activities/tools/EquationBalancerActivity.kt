package com.jlindemann.science.activities.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.textfield.TextInputEditText
import com.jlindemann.science.R
import com.jlindemann.science.activities.BaseActivity
import com.jlindemann.science.ai.data.ElementTally
import com.jlindemann.science.ai.data.EquationBalancer
import com.jlindemann.science.ai.data.Term
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.ElementModel
import com.jlindemann.science.preferences.MostUsedToolPreference
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

        findViewById<View>(R.id.balancer_copy_btn).setOnClickListener { copyResult() }
        for (id in listOf(R.id.example_1, R.id.example_2, R.id.example_3)) {
            val chip = findViewById<TextView>(id)
            chip.setOnClickListener {
                findViewById<TextInputEditText>(R.id.edit_text_balancer).setText(chip.text)
            }
        }
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

        val sides = EquationBalancer.parseEquation(input) { it in symbols }
        if (sides == null) {
            // No arrow yet is the normal state while typing, and calling that a parse failure
            // would scold the user for every keystroke up to the "->".
            show(getString(R.string.balancer_tool_prompt))
            return
        }

        when (val result = EquationBalancer.balance(sides.first, sides.second)) {
            is EquationBalancer.Result.Balanced -> show(
                equationOf(result.reactants, result.products),
                proofOf(result.tally)
            )
            is EquationBalancer.Result.Failed -> show(getString(messageFor(result.reason)))
        }
    }

    private fun equationOf(reactants: List<Term>, products: List<Term>): String {
        fun side(terms: List<Term>) = terms.joinToString(" + ") { term ->
            // A coefficient of 1 is written, not printed: "1Fe" is what a bug looks like.
            if (term.coefficient == 1) term.formula else "${term.coefficient}${term.formula}"
        }
        return "${side(reactants)} → ${side(products)}"
    }

    private fun proofOf(tally: List<ElementTally>): String =
        tally.joinToString("\n") {
            getString(R.string.ai_equation_tally_row, it.symbol, it.left, it.right)
        }

    private fun messageFor(reason: EquationBalancer.Reason): Int = when (reason) {
        EquationBalancer.Reason.ONE_SIDED_ELEMENT -> R.string.ai_equation_one_sided
        EquationBalancer.Reason.UNDERDETERMINED -> R.string.ai_equation_underdetermined
        EquationBalancer.Reason.TOO_LARGE -> R.string.ai_equation_too_large
        EquationBalancer.Reason.PARSE_FAILED -> R.string.ai_equation_parse_failed
        EquationBalancer.Reason.NO_SOLUTION -> R.string.ai_equation_no_solution
    }

    private fun copyResult() {
        val text = findViewById<TextView>(R.id.balancer_out_text).text.toString()
        if (text.isBlank() || text == getString(R.string.balancer_tool_prompt)) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("equation", text))
        ToastUtil.showToast(this, getString(R.string.balancer_tool_copied))
    }

    /** Same counter the other tools bump, so the most-used row reflects real use. */
    private fun bumpMostUsed() {
        val preference = MostUsedToolPreference(this)
        val current = preference.getValue()
        val match = Regex("(bal)=(\\d+\\.\\d+)").find(current) ?: return
        val value = match.groups[2]!!.value.toDouble()
        preference.setValue(current.replace("bal=$value", "bal=${value + 1}"))
    }
}
