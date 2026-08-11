package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

/**
 * @param input the equation as the user typed it — the row's identity, and what a tap puts back
 *   into the input field.
 * @param balanced the same equation with its coefficients, as it was shown when saved.
 */
data class FavoriteEquation(val input: String, val balanced: String)

/**
 * The saved equations on the balancer screen.
 *
 * The row menu mirrors the calculator's favorites (copy / remove) and inflates the same menu, so
 * the two screens do not drift apart. Tapping the row itself restores the equation, which the
 * calculator has no equivalent of: a molar mass is the whole answer, while an equation is
 * something the user usually wants back in the box to edit.
 */
class FavoriteEquationsAdapter(
    private val onRemove: (String) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onRestore: (String) -> Unit
) : RecyclerView.Adapter<FavoriteEquationsAdapter.ViewHolder>() {

    private val equations = mutableListOf<FavoriteEquation>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val inputTextView: TextView = itemView.findViewById(R.id.equation_input_text)
        val balancedTextView: TextView = itemView.findViewById(R.id.equation_balanced_text)
        val optionsButton: TextView = itemView.findViewById(R.id.options_btn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_equation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val equation = equations[position]
        holder.inputTextView.text = equation.input
        holder.balancedTextView.text = equation.balanced

        holder.itemView.setOnClickListener { onRestore(equation.input) }

        holder.optionsButton.setOnClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, holder.optionsButton)
            popupMenu.menuInflater.inflate(R.menu.favorite_compound_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    // The balanced form is what is worth having on the clipboard; the typed form
                    // is already whatever the user wrote.
                    R.id.action_copy -> {
                        onCopy(equation.balanced)
                        true
                    }
                    R.id.action_remove -> {
                        onRemove(equation.input)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    override fun getItemCount(): Int = equations.size

    fun updateEquations(newEquations: List<FavoriteEquation>) {
        equations.clear()
        equations.addAll(newEquations)
        notifyDataSetChanged()
    }
}
