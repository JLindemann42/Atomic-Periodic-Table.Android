package com.jlindemann.science.activities

import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

data class FavoriteCompound(val compound: String, val molarWeight: String)

class FavoriteCompoundsAdapter(
    private val onRemove: (String) -> Unit,
    private val onCopy: (String) -> Unit
) : RecyclerView.Adapter<FavoriteCompoundsAdapter.ViewHolder>() {

    private val compounds = mutableListOf<FavoriteCompound>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val compoundTextView: TextView = itemView.findViewById(R.id.compound_text)
        val molarWeightTextView: TextView = itemView.findViewById(R.id.molar_weight_text)
        val optionsButton: TextView= itemView.findViewById(R.id.options_btn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_compound, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val compound = compounds[position]
        holder.compoundTextView.text = formatCompoundText(compound.compound)
        holder.molarWeightTextView.text = compound.molarWeight

        holder.optionsButton.setOnClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, holder.optionsButton)
            popupMenu.menuInflater.inflate(R.menu.favorite_compound_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_copy -> {
                        onCopy(compound.compound)
                        true
                    }
                    R.id.action_remove -> {
                        onRemove(compound.compound)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    override fun getItemCount(): Int = compounds.size

    fun updateCompounds(newCompounds: List<FavoriteCompound>) {
        compounds.clear()
        compounds.addAll(newCompounds)
        notifyDataSetChanged()
    }

    private fun formatCompoundText(text: String): SpannableString {
        val spannableString = SpannableString(text)
        val elementRegex = Regex("([A-Z][a-z]*)(\\d*)")
        val groupRegex = Regex("(\\)|\\])(\\d+)")

        elementRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val element = matchResult.groupValues[1]
            val number = matchResult.groupValues[2]

            if (number.isNotEmpty()) {
                spannableString.setSpan(android.text.style.SubscriptSpan(), start + element.length, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        groupRegex.findAll(text).forEach { matchResult ->
            spannableString.setSpan(android.text.style.SubscriptSpan(), matchResult.range.first + 1, matchResult.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannableString
    }
}