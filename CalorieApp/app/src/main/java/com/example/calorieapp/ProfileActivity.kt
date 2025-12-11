package com.example.calorieapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var logoutButton: MaterialButton

    private lateinit var usernameText: TextView
    private lateinit var dobText: TextView
    private lateinit var heightText: TextView
    private lateinit var weightText: TextView
    private lateinit var genderText: TextView
    private lateinit var bloodGroupText: TextView

    private lateinit var caloriesGoalText: TextView
    private lateinit var proteinGoalText: TextView
    private lateinit var carbsGoalText: TextView
    private lateinit var fatGoalText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_profile)

        GoalManager.init(this)

        bottomNavView = findViewById(R.id.bottom_navigation)
        logoutButton = findViewById(R.id.logout_button)

        usernameText = findViewById(R.id.username_text)
        dobText = findViewById(R.id.dob_value)
        heightText = findViewById(R.id.height_value)
        weightText = findViewById(R.id.weight_value)
        genderText = findViewById(R.id.gender_value)
        bloodGroupText = findViewById(R.id.blood_group_value)

        caloriesGoalText = findViewById(R.id.goal_calories_text)
        proteinGoalText = findViewById(R.id.goal_protein_text)
        carbsGoalText = findViewById(R.id.goal_carbs_text)
        fatGoalText = findViewById(R.id.goal_fat_text)

        setupBottomNavigation()
        populateProfileData()
        populateGoalData()

        logoutButton.setOnClickListener {
            SessionManager.clearSession()
            SessionManager.clearMealLogCache()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun String?.orNA(): String {
        return if (this.isNullOrEmpty() || this == "null") "N/A" else this
    }

    private fun formatDob(dateString: String?): String {
        if (dateString.isNullOrEmpty() || dateString == "null") return "N/A"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            formatter.format(parser.parse(dateString)!!)
        } catch (e: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                formatter.format(parser.parse(dateString)!!)
            } catch (e2: Exception) {
                dateString.split("T").firstOrNull() ?: "N/A"
            }
        }
    }

    private fun populateProfileData() {
        val profile = SessionManager.currentProfile
        if (profile == null) {
            usernameText.text = "Error"
            return
        }
        usernameText.text = profile.name.orNA()
        dobText.text = formatDob(profile.dob)
        genderText.text = profile.gender.orNA().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        heightText.text = profile.height_cm?.let { "${it.toInt()} cm" } ?: "Not Set"
        weightText.text = profile.weight_kg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "Not Set"
        bloodGroupText.text = "N/A"
    }

    private fun populateGoalData() {
        // Read goals (No Sodium)
        caloriesGoalText.text = "${GoalManager.getGoalCalories()} kcal"
        proteinGoalText.text = "${GoalManager.getGoalProtein()} g"
        carbsGoalText.text = "${GoalManager.getGoalCarbs()} g"
        fatGoalText.text = "${GoalManager.getGoalFat()} g"
    }

    private fun setupBottomNavigation() {
        bottomNavView.selectedItemId = R.id.nav_profile
        bottomNavView.setOnItemSelectedListener { item ->
            val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent, options.toBundle())
                    true
                }
                R.id.nav_camera -> {
                    val intent = Intent(this, FoodScanActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent, options.toBundle())
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
        bottomNavView.setOnItemReselectedListener { /* Do nothing */ }
    }

    override fun onResume() {
        super.onResume()
        populateProfileData()
        populateGoalData()
        bottomNavView.selectedItemId = R.id.nav_profile
    }
}