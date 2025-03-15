package com.jlindemann.science.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jlindemann.science.R
import com.jlindemann.science.activities.tables.EmissionActivity
import com.jlindemann.science.activities.tables.IonActivity
import com.jlindemann.science.model.Element
import com.jlindemann.science.model.Equation
import com.jlindemann.science.model.Ion
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException


class EmissionAdapter(var list: ArrayList<Element>, var clickListener: EmissionActivity, val context: Context) : RecyclerView.Adapter<EmissionAdapter.ViewHolder>() {
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.initialize(list[position], clickListener, context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.row_emission_list, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int { return list.size }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView.findViewById(R.id.emission_row) as FrameLayout
        private val emissionImg = itemView.findViewById(R.id.emission_img) as ImageView
        private val shortEmi = itemView.findViewById(R.id.emi_short) as TextView
        private val nameEmi = itemView.findViewById(R.id.emi_name) as TextView

        fun initialize(item: Element, action: OnEmissionClickListener, context: Context) {
            var jsonString : String? = null
            try {
                val ext = ".json"
                val element = item.element
                val ElementJson: String? = "$element$ext"

                val inputStream: InputStream = context.assets.open(ElementJson.toString())
                jsonString = inputStream.bufferedReader().use { it.readText() }

                val jsonArray = JSONArray(jsonString)
                val jsonObject: JSONObject = jsonArray.getJSONObject(0)

                val url = jsonObject.optString("short", "---")
                val hUrl = "https://www.jlindemann.se/atomic/emission_lines/"
                val extg = ".gif"
                val fURL = hUrl + url + extg
                try {
                    Picasso.get().load(fURL).into(emissionImg)
                }
                catch(e: ConnectException) {
                    //findViewById<ImageView>(R.id.sp_img).visibility = View.GONE
                    //findViewById<TextView>(R.id.sp_offline).text = "No Data"
                    //findViewById<TextView>(R.id.sp_offline).visibility = View.VISIBLE
                }
            }
            catch (e: IOException) { }

            shortEmi.text = item.short
            nameEmi.text = item.element.capitalize()

            cardView.foreground = ContextCompat.getDrawable(context, R.drawable.toast_card_ripple)
            cardView.isClickable = true
            cardView.isFocusable = true
            cardView.setOnClickListener {
                action.emiClickListener(item, adapterPosition)
            }

        }
    }

    fun filterList(filteredList: ArrayList<Element>) {
        list = filteredList
        notifyDataSetChanged()

    }
    interface OnEmissionClickListener {
        fun emiClickListener(item: Element, position: Int)
    }
}


