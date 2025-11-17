package com.jlindemann.science.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.ProductDetails
import com.jlindemann.science.R
import com.jlindemann.science.activities.IntroductionActivity

class IntroductionPagerAdapter(private val activity: IntroductionActivity) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_PRO = 1
    }

    private val pages = listOf(
        IntroPage(
            R.drawable.intro_splash,
            R.string.intro_welcome_title,
            R.string.intro_welcome_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.intro_flashcards,
            R.string.intro_flashcards_title,
            R.string.intro_flashcards_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.intro_calculator,
            R.string.intro_calculator_title,
            R.string.intro_calculator_description,
            VIEW_TYPE_NORMAL
        ),
        IntroPage(
            R.drawable.intro_pro,
            R.string.get_pro_title,
            R.string.pro_descr,
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

        // The pro options layout should include these IDs (same as used in ProActivity)
        val proBuyBtn: TextView? = view.findViewById(R.id.pro_buy_btn)
        val proPlusBuyBtn: TextView? = view.findViewById(R.id.pro_plus_buy_btn)
        val proPriceView: TextView? = view.findViewById(R.id.pro_price)
        val proPlusPriceView: TextView? = view.findViewById(R.id.pro_plus_price)
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

                // Populate price text and button states from the activity's billing state
                val proDetails: ProductDetails? = activity.getProductDetail("pro_version")
                val proPlusDetails: ProductDetails? = activity.getProductDetail("pro_version_plus")
                val proPlusUpgradeDetails: ProductDetails? = activity.getProductDetail("pro_version_plus_upgrade")

                val ownsPro = activity.isOwnsProVersion()
                val ownsProPlus = activity.isOwnsProPlusVersion()

                // Update PRO button and price
                if (ownsPro && !ownsProPlus) {
                    holder.proBuyBtn?.isEnabled = false
                    holder.proBuyBtn?.text = activity.getString(R.string.current_version)
                    holder.proPriceView?.text = "---"
                } else if (ownsProPlus) {
                    holder.proBuyBtn?.isEnabled = false
                    holder.proBuyBtn?.text = activity.getString(R.string.owns_pro_plus)
                    holder.proPriceView?.text = "---"
                } else {
                    holder.proBuyBtn?.isEnabled = true
                    holder.proBuyBtn?.text = activity.getString(R.string.get_pro)
                    holder.proPriceView?.text = activity.getFormattedPrice(proDetails)
                }

                // Update PRO+ button and price
                if (ownsProPlus) {
                    holder.proPlusBuyBtn?.isEnabled = false
                    holder.proPlusBuyBtn?.text = activity.getString(R.string.current_version)
                    holder.proPlusPriceView?.text = "---"
                } else if (ownsPro && !ownsProPlus) {
                    holder.proPlusBuyBtn?.isEnabled = true
                    holder.proPlusBuyBtn?.text = activity.getString(R.string.upgrade_to_pro_plus)
                    holder.proPlusPriceView?.text = activity.getFormattedPrice(proPlusUpgradeDetails)
                } else {
                    holder.proPlusBuyBtn?.isEnabled = true
                    holder.proPlusBuyBtn?.text = activity.getString(R.string.get_pro_plus)
                    holder.proPlusPriceView?.text = activity.getFormattedPrice(proPlusDetails)
                }

                // Attach click listeners that call back into activity
                holder.proBuyBtn?.setOnClickListener {
                    if (!ownsPro && !ownsProPlus) {
                        proDetails?.let { activity.purchaseProduct(it) }
                    }
                }
                holder.proPlusBuyBtn?.setOnClickListener {
                    if (!ownsProPlus) {
                        if (ownsPro) {
                            proPlusUpgradeDetails?.let { activity.purchaseProduct(it) }
                        } else {
                            proPlusDetails?.let { activity.purchaseProduct(it) }
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = pages.size
}
