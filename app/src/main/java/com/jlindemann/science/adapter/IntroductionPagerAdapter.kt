package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

class IntroductionPagerAdapter(private val activity: android.app.Activity) : 
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_PRO = 1
    }
    
    private val pages = listOf(
        IntroPage(
            R.drawable.ic_launcher,
            R.string.intro_welcome_title,
            R.string.intro_welcome_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.additional_flashcards,
            R.string.intro_flashcards_title,
            R.string.intro_flashcards_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.additional_calculator_2,
            R.string.intro_calculator_title,
            R.string.intro_calculator_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.additional_data_2,
            R.string.intro_pro_title,
            R.string.intro_pro_description,
            VIEW_TYPE_PRO
        )
    )
    
    data class IntroPage(
        val imageRes: Int,
        val titleRes: Int,
        val descriptionRes: Int,
        val viewType: Int
    )
    
    inner class IntroPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.intro_image)
        val title: TextView = view.findViewById(R.id.intro_title)
        val description: TextView = view.findViewById(R.id.intro_description)
    }
    
    inner class ProPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.intro_pro_image)
        val title: TextView = view.findViewById(R.id.intro_pro_title)
        val description: TextView = view.findViewById(R.id.intro_pro_description)
        // The pro_options_layout is included in the layout
    }
    
    override fun getItemViewType(position: Int): Int {
        return pages[position].viewType
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_PRO) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_introduction_pro_page, parent, false)
            ProPageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_introduction_page, parent, false)
            IntroPageViewHolder(view)
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val page = pages[position]
        when (holder) {
            is IntroPageViewHolder -> {
                holder.image.setImageResource(page.imageRes)
                holder.title.setText(page.titleRes)
                holder.description.setText(page.descriptionRes)
            }
            is ProPageViewHolder -> {
                holder.image.setImageResource(page.imageRes)
                holder.title.setText(page.titleRes)
                holder.description.setText(page.descriptionRes)
                // Pro options layout is already included via XML
            }
        }
    }
    
    override fun getItemCount(): Int = pages.size
}
