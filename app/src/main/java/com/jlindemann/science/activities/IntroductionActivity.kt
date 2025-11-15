import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jlindemann.science.R
import com.jlindemann.science.activities.MainActivity
import com.jlindemann.science.adapter.IntroductionPagerAdapter
import com.jlindemann.science.preferences.ThemePreference

class IntroductionActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme
        val themePreference = ThemePreference(this)
        val themePrefValue = themePreference.getValue()
        if (themePrefValue == 100) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { setTheme(R.style.AppTheme) }
                Configuration.UI_MODE_NIGHT_YES -> { setTheme(R.style.AppThemeDark) }
            }
        }
        if (themePrefValue == 0) { setTheme(R.style.AppTheme) }
        if (themePrefValue == 1) { setTheme(R.style.AppThemeDark) }
        
        setContentView(R.layout.activity_introduction)
        
        viewPager = findViewById(R.id.intro_viewpager)
        tabLayout = findViewById(R.id.intro_tab_layout)
        nextButton = findViewById(R.id.intro_next_button)
        skipButton = findViewById(R.id.intro_skip_button)
        backButton = findViewById(R.id.intro_back_button)
        
        // Setup ViewPager
        val adapter = IntroductionPagerAdapter(this)
        viewPager.adapter = adapter
        
        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
        
        // Setup navigation buttons
        nextButton.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                completeIntroduction()
            }
        }
        
        skipButton.setOnClickListener {
            completeIntroduction()
        }
        
        backButton.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem -= 1
            }
        }
        
        // Update button states based on current page
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonStates(position, adapter.itemCount)
            }
        })
        
        // Initial button state
        updateButtonStates(0, adapter.itemCount)
        
        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) {
                    viewPager.currentItem -= 1
                } else {
                    finish()
                }
            }
        })
    }
    
    private fun updateButtonStates(position: Int, totalPages: Int) {
        // Show/hide back button
        backButton.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        
        // Update next/finish button text
        if (position == totalPages - 1) {
            nextButton.text = getString(R.string.intro_finish)
            skipButton.visibility = View.GONE
        } else {
            nextButton.text = getString(R.string.intro_next)
            skipButton.visibility = View.VISIBLE
        }
    }
    
    private fun completeIntroduction() {
        // Mark introduction as completed
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("introduction_shown", true).apply()
        
        // Navigate to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
