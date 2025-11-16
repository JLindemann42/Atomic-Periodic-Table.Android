package com.jlindemann.science

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.preferences.ElementSendAndLoad
import com.jlindemann.science.utils.ElementDataLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

/**
 * Implementation of Element of the Day widget.
 * Displays a different element each day with image, name, symbol, and description.
 */
class ElementOfTheDayWidget : AppWidgetProvider() {
    
    companion object {
        // List of all element keys in order
        private val ELEMENT_KEYS = listOf(
            "hydrogen", "helium", "lithium", "beryllium", "boron", "carbon", "nitrogen", "oxygen",
            "fluorine", "neon", "sodium", "magnesium", "aluminium", "silicon", "phosphorus", "sulfur",
            "chlorine", "argon", "potassium", "calcium", "scandium", "titanium", "vanadium", "chromium",
            "manganese", "iron", "cobalt", "nickel", "copper", "zinc", "gallium", "germanium",
            "arsenic", "selenium", "bromine", "krypton", "rubidium", "strontium", "yttrium", "zirconium",
            "niobium", "molybdenum", "technetium", "ruthenium", "rhodium", "palladium", "silver", "cadmium",
            "indium", "tin", "antimony", "tellurium", "iodine", "xenon", "caesium", "barium",
            "lanthanum", "cerium", "praseodymium", "neodymium", "promethium", "samarium", "europium", "gadolinium",
            "terbium", "dysprosium", "holmium", "erbium", "thulium", "ytterbium", "lutetium", "hafnium",
            "tantalum", "tungsten", "rhenium", "osmium", "iridium", "platinum", "gold", "mercury",
            "thallium", "lead", "bismuth", "polonium", "astatine", "radon", "francium", "radium",
            "actinium", "thorium", "protactinium", "uranium", "neptunium", "plutonium", "americium", "curium",
            "berkelium", "californium", "einsteinium", "fermium", "mendelevium", "nobelium", "lawrencium", "rutherfordium",
            "dubnium", "seaborgium", "bohrium", "hassium", "meitnerium", "darmstadtium", "roentgenium", "copernicium",
            "nihonium", "flerovium", "moscovium", "livermorium", "tennessine", "oganesson"
        )
        
        /**
         * Get the element of the day based on current date
         */
        fun getElementOfTheDay(): String {
            val calendar = Calendar.getInstance()
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            // Use modulo to cycle through all 118 elements
            val index = (dayOfYear - 1) % ELEMENT_KEYS.size
            return ELEMENT_KEYS[index]
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ElementOfTheDayWidget::class.java)
        )
        for (appWidgetId in widgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        // Called when the first widget is created
    }
    
    override fun onDisabled(context: Context) {
        // Called when the last widget is removed
    }
    
    /**
     * Update a single widget instance with element data
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Get element of the day
        val elementKey = getElementOfTheDay()
        
        // Create RemoteViews
        val views = RemoteViews(context.packageName, R.layout.element_of_the_day_widget)
        
        // Set up click intent to open the app with this element
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // Create intent to open ElementInfoActivity with the element
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("widget_element_key", elementKey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        
        // Load element data asynchronously
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            try {
                // Load element data
                val jsonObject = ElementDataLoader.loadElementData(context, elementKey)
                
                if (jsonObject != null) {
                    val elementName = jsonObject.optString("element", "---")
                    val symbol = jsonObject.optString("short", "---")
                    val atomicNumber = jsonObject.optString("element_atomic_number", "---")
                    val description = jsonObject.optString("description", "---")
                    val imageUrl = jsonObject.optString("link", "")
                    
                    // Update widget views
                    views.setTextViewText(R.id.widget_element_name, elementName)
                    views.setTextViewText(R.id.widget_element_symbol, symbol)
                    views.setTextViewText(R.id.widget_atomic_number, atomicNumber)
                    views.setTextViewText(R.id.widget_element_description, description)
                    
                    // Note: Loading images in widgets requires special handling
                    // For simplicity, we'll just show the element data without image for now
                    // Images can be loaded using Glide with AppWidgetTarget if needed
                    
                    // Update the widget
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } else {
                    // Fallback if element data couldn't be loaded
                    views.setTextViewText(R.id.widget_element_name, "Element of the Day")
                    views.setTextViewText(R.id.widget_element_symbol, "?")
                    views.setTextViewText(R.id.widget_atomic_number, "---")
                    views.setTextViewText(R.id.widget_element_description, "Unable to load element data")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                // Handle errors gracefully
                views.setTextViewText(R.id.widget_element_name, "Element of the Day")
                views.setTextViewText(R.id.widget_element_symbol, "?")
                views.setTextViewText(R.id.widget_atomic_number, "---")
                views.setTextViewText(R.id.widget_element_description, "Error loading element")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        
        // Update with basic info immediately (before async load completes)
        views.setTextViewText(R.id.widget_element_name, "Loading...")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
