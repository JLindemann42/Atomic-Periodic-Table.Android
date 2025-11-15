package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R

class IntroductionPagerAdapter(private val activity: android.app.Activity) : 
    RecyclerView.Adapter<IntroductionPagerAdapter.IntroPageViewHolder>() {
    
    private val pages = listOf(
        IntroPage(
            R.drawable.ic_launcher,
            R.string.intro_welcome_title,
            R.string.intro_welcome_description
        ),
        IntroPage(
            R.drawable.additional_flashcards,
            R.string.intro_flashcards_title,
            R.string.intro_flashcards_description
        ),
        IntroPage(
            R.drawable.additional_calculator_2,
            R.string.intro_calculator_title,
            R.string.intro_calculator_description
        ),
        IntroPage(
            R.drawable.additional_data_2,
            R.string.intro_pro_title,
            R.string.intro_pro_description
        )
    )
    
    data class IntroPage(
        val imageRes: Int,
        val titleRes: Int,
        val descriptionRes: Int
    )
    
    inner class IntroPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.intro_image)
        val title: TextView = view.findViewById(R.id.intro_title)
        val description: TextView = view.findViewById(R.id.intro_description)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroPageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_introduction_page, parent, false)
        return IntroPageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: IntroPageViewHolder, position: Int) {
        val page = pages[position]
        holder.image.setImageResource(page.imageRes)
        holder.title.setText(page.titleRes)
        holder.description.setText(page.descriptionRes)
    }
    
    override fun getItemCount(): Int = pages.size
}
