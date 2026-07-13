package com.jlindemann.science.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.jlindemann.science.R

class UnifiedTitleBarController(private val root: View) {
    // When used via <include android:id="...">, Android replaces the included root id
    // with the include id. Fall back to the passed root in that case.
    val container: View = root.findViewById(R.id.unified_titlebar_root) ?: root
    val backButton: MaterialButton = root.findViewById(R.id.unified_titlebar_back_btn)
    val titleView: TextView = root.findViewById(R.id.unified_titlebar_title)
    val actionButton: MaterialButton = root.findViewById(R.id.unified_titlebar_action_btn)
    val titleRow: View = root.findViewById(R.id.unified_titlebar_row)
    val searchRow: View = root.findViewById(R.id.unified_titlebar_search_row)
    val searchCloseButton: MaterialButton = root.findViewById(R.id.unified_titlebar_search_close_btn)
    val searchInput: EditText = root.findViewById(R.id.unified_titlebar_search_input)
    val categoriesScroll: View = root.findViewById(R.id.unified_titlebar_categories_scroll)
    val categoriesGroup: ChipGroup = root.findViewById(R.id.unified_titlebar_categories_group)

    fun setTitle(@StringRes titleRes: Int) {
        titleView.setText(titleRes)
    }

    fun setAction(@DrawableRes iconRes: Int, onClick: () -> Unit) {
        actionButton.visibility = View.VISIBLE
        actionButton.setIconResource(iconRes)
        actionButton.setOnClickListener { onClick() }
    }

    fun hideAction() {
        actionButton.visibility = View.GONE
    }

    fun setSearchHint(@StringRes hintRes: Int) {
        searchInput.setHint(hintRes)
    }

    fun showSearch() {
        titleRow.visibility = View.GONE
        searchRow.visibility = View.VISIBLE
        searchInput.requestFocus()
        val imm = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSearch() {
        searchRow.visibility = View.GONE
        titleRow.visibility = View.VISIBLE
        val imm = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    fun setCategories(
        categories: List<Pair<Int, String>>,
        onCategorySelected: (Int) -> Unit
    ) {
        categoriesGroup.removeAllViews()
        if (categories.isEmpty()) {
            categoriesScroll.visibility = View.GONE
            return
        }
        categoriesScroll.visibility = View.VISIBLE
        categories.forEach { (id, text) ->
            val chip = Chip(root.context).apply {
                this.id = id
                this.text = text
                isCheckable = true
                isClickable = true
                setOnClickListener { onCategorySelected(id) }
            }
            categoriesGroup.addView(chip)
        }
    }

    fun hideCategories() {
        categoriesScroll.visibility = View.GONE
    }
}
