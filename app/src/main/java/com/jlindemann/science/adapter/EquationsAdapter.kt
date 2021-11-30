package com.jlindemann.science.adapter

import android.content.Context
import android.content.res.Configuration
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.EquationsActivity
import com.jlindemann.science.model.Equation
import com.jlindemann.science.preferences.ThemePreference

class EquationsAdapter(var list: ArrayList<Equation>, var clickListener: EquationsActivity, val context: Context) : RecyclerView.Adapter<EquationsAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_equations_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val equTitle = itemView.findViewById(R.id.tv_equ) as TextView
        private val equCategory = itemView.findViewById(R.id.ic_equ) as TextView
        private val equImg = itemView.findViewById(R.id.ic_eq_view) as ImageView

        fun initialize(item: Equation, action: OnEquationClickListener, context: Context) {
            equTitle.text = item.equationTitle
            if (item.category == "Mechanics") { equCategory.text = "Me" }
            if (item.category == "General") { equCategory.text = "Ge" }
            if (item.category == "Theory of Relativity") { equCategory.text = "TR" }
            if (item.category == "Thermodynamics") { equCategory.text = "Th" }
            if (item.category == "Wavelengths") { equCategory.text = "Wv" }
            if (item.category == "Electricity") { equCategory.text = "El" }
            if (item.category == "Magnetism and Induction") { equCategory.text = "MI" }
            if (item.category == "Atomic Physics") { equCategory.text = "AP" }
            if (item.category == "Nuclear Physics") { equCategory.text = "NP" }
            equImg.setImageResource(item.equation)
            val themePreference = ThemePreference(context)
            val themePrefValue = themePreference.getValue()
            if (themePrefValue == 1) {
                equImg.colorFilter = ColorMatrixColorFilter(NEGATIVE)
            }
            if (themePrefValue == 100) {
                when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> { equImg.colorFilter = ColorMatrixColorFilter(NEGATIVE) }
                }
            }
            itemView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setOnClickListener {
                action.equationClickListener(item, adapterPosition)
            }
        }
        private val NEGATIVE = floatArrayOf(
            -1.0f, 0f, 0f, 0f, 255f,
            0f, -1.0f, 0f, 0f, 255f,
            0f, 0f, -1.0f, 0f, 255f,
            0f, 0f, 0f, 1.0f, 0f
        )
    }


    fun filterList(filteredList: ArrayList<Equation>) {
        list = filteredList
        notifyDataSetChanged()
    }

    interface OnEquationClickListener {
        fun equationClickListener(item: Equation, position: Int)
    }

}


