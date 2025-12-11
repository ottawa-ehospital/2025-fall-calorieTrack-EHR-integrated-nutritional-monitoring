package com.example.calorieapp

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var welcomeTextView: TextView
    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var viewHistoryButton: Button
    private lateinit var dashboardProgressBar: ProgressBar

    // Calorie Ring
    private lateinit var caloriesProgress: CircularProgressIndicator
    private lateinit var tvCaloriesValue: TextView
    private lateinit var tvCaloriesGoal: TextView
    private lateinit var btnEditGoals: MaterialButton

    // Macros (No Sodium)
    private lateinit var tvProteinVal: TextView
    private lateinit var progressProtein: LinearProgressIndicator
    private lateinit var tvCarbsVal: TextView
    private lateinit var progressCarbs: LinearProgressIndicator
    private lateinit var tvFatVal: TextView
    private lateinit var progressFat: LinearProgressIndicator

    // BMI
    private lateinit var tvBmiValue: TextView
    private lateinit var tvBmiCategory: TextView
    private lateinit var progressBmi: LinearProgressIndicator

    companion object {
        const val EXTRA_FIRST_NAME = "com.example.calorieapp.FIRST_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_page)

        GoalManager.init(this)

        welcomeTextView = findViewById(R.id.welcome_message)
        bottomNavView = findViewById(R.id.bottom_navigation)
        viewHistoryButton = findViewById(R.id.view_history_button)
        dashboardProgressBar = findViewById(R.id.dashboard_progress_bar)

        caloriesProgress = findViewById(R.id.calories_progress_indicator)
        tvCaloriesValue = findViewById(R.id.tv_calories_value)
        tvCaloriesGoal = findViewById(R.id.tv_calories_goal)
        btnEditGoals = findViewById(R.id.btn_edit_goals)

        tvProteinVal = findViewById(R.id.tv_protein_val)
        progressProtein = findViewById(R.id.progress_protein_bar)
        tvCarbsVal = findViewById(R.id.tv_carbs_val)
        progressCarbs = findViewById(R.id.progress_carbs_bar)
        tvFatVal = findViewById(R.id.tv_fat_val)
        progressFat = findViewById(R.id.progress_fat_bar)

        tvBmiValue = findViewById(R.id.tv_bmi_value)
        tvBmiCategory = findViewById(R.id.tv_bmi_category)
        progressBmi = findViewById(R.id.progress_bmi)

        val firstName = intent.getStringExtra(EXTRA_FIRST_NAME)
        welcomeTextView.text = if (firstName.isNullOrEmpty() || firstName == "N/A") "Hello User!" else "Hello $firstName!"

        setupBottomNavigation()

        viewHistoryButton.setOnClickListener {
            val intent = Intent(this, MealLogActivity::class.java)
            startActivity(intent)
        }

        btnEditGoals.setOnClickListener {
            showEditGoalsDialog()
        }
    }

    private fun showEditGoalsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_goals, null)

        // Find views INSIDE the dialog view
        val editCalories = dialogView.findViewById<TextInputEditText>(R.id.edit_goal_calories)
        val editProtein = dialogView.findViewById<TextInputEditText>(R.id.edit_goal_protein)
        val editCarbs = dialogView.findViewById<TextInputEditText>(R.id.edit_goal_carbs)
        val editFat = dialogView.findViewById<TextInputEditText>(R.id.edit_goal_fat)

        // Pre-fill values
        editCalories.setText(GoalManager.getGoalCalories().toString())
        editProtein.setText(GoalManager.getGoalProtein().toString())
        editCarbs.setText(GoalManager.getGoalCarbs().toString())
        editFat.setText(GoalManager.getGoalFat().toString())

        // Create Dialog
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { dialog: DialogInterface, which: Int ->
                val c = editCalories.text.toString().toIntOrNull()
                val p = editProtein.text.toString().toIntOrNull()
                val cb = editCarbs.text.toString().toIntOrNull()
                val f = editFat.text.toString().toIntOrNull()

                // Save goals (Sodium removed)
                GoalManager.saveGoals(c, p, cb, f)
                Toast.makeText(this, "Goals Updated!", Toast.LENGTH_SHORT).show()
                loadMealLog() // Refresh UI
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMealLog() {
        dashboardProgressBar.visibility = View.VISIBLE

        val patientId = SessionManager.currentProfile?.user?.userId
        if (patientId == null) {
            dashboardProgressBar.visibility = View.GONE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val log = SessionManager.getMealLog(patientId)
                val summary = processLog(log)

                withContext(Dispatchers.Main) {
                    updateDashboardUI(summary)
                    updateBMI()
                    dashboardProgressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to load log", e)
                withContext(Dispatchers.Main) {
                    dashboardProgressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun isToday(date: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date
        val cal2 = Calendar.getInstance()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun processLog(log: List<MealLog>?): DailySummary {
        val summary = DailySummary()
        if (log == null) return summary
        val todayLog = log.filter { isToday(it.loggedAt) }
        todayLog.forEach { meal ->
            summary.calories += meal.calories
            summary.protein += meal.protein
            summary.carbs += meal.carbs
            summary.fat += meal.fat
        }
        return summary
    }

    private fun updateDashboardUI(summary: DailySummary) {
        val goalCal = GoalManager.getGoalCalories().toDouble()
        val goalProt = GoalManager.getGoalProtein().toDouble()
        val goalCarb = GoalManager.getGoalCarbs().toDouble()
        val goalFat = GoalManager.getGoalFat().toDouble()

        // 1. Calories Ring
        tvCaloriesValue.text = summary.calories.toInt().toString()
        tvCaloriesGoal.text = "Goal: ${goalCal.toInt()}"

        val calProgress = if (goalCal > 0) (summary.calories / goalCal * 100).toInt() else 0
        caloriesProgress.setProgress(calProgress, true)

        // Color Logic for Calories
        if (calProgress > 100) {
            caloriesProgress.setIndicatorColor(0xFFE57373.toInt()) // Red
        } else {
            caloriesProgress.setIndicatorColor(0xFF0C457D.toInt()) // Blue
        }

        // 2. Protein
        tvProteinVal.text = "${summary.protein.toInt()}g"
        val pProg = if (goalProt > 0) (summary.protein / goalProt * 100).toInt() else 0
        progressProtein.setProgress(pProg, true)

        // 3. Carbs
        tvCarbsVal.text = "${summary.carbs.toInt()}g"
        val cProg = if (goalCarb > 0) (summary.carbs / goalCarb * 100).toInt() else 0
        progressCarbs.setProgress(cProg, true)

        // 4. Fat
        tvFatVal.text = "${summary.fat.toInt()}g"
        val fProg = if (goalFat > 0) (summary.fat / goalFat * 100).toInt() else 0
        progressFat.setProgress(fProg, true)
    }

    private fun updateBMI() {
        val profile = SessionManager.currentProfile
        if (profile == null || profile.weight_kg == null || profile.height_cm == null) {
            tvBmiValue.text = "--"
            tvBmiCategory.text = "Update profile to see BMI"
            progressBmi.progress = 0
            return
        }

        val weight = profile.weight_kg
        val heightM = profile.height_cm / 100.0 // cm to meters

        if (heightM > 0) {
            val bmi = weight / (heightM * heightM)
            tvBmiValue.text = String.format(Locale.US, "%.1f", bmi)
            progressBmi.setProgress(bmi.toInt(), true)

            val (category, colorInt) = when {
                bmi < 18.5 -> "Underweight" to 0xFFFFCA28.toInt() // Yellow
                bmi < 25.0 -> "Normal Weight" to 0xFF4CAF50.toInt() // Green
                bmi < 30.0 -> "Overweight" to 0xFFFFCA28.toInt() // Yellow
                else -> "Obese" to 0xFFF44336.toInt() // Red
            }
            tvBmiCategory.text = category
            tvBmiCategory.setTextColor(colorInt)
            progressBmi.setIndicatorColor(colorInt)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavView.selectedItemId = R.id.nav_home
        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_camera -> {
                    val intent = Intent(this, FoodScanActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
                    startActivity(intent, options.toBundle())
                    true
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
                    startActivity(intent, options.toBundle())
                    true
                }
                else -> false
            }
        }
        bottomNavView.setOnItemReselectedListener { /* Do nothing */ }
    }

    override fun onResume() {
        super.onResume()
        bottomNavView.selectedItemId = R.id.nav_home
        loadMealLog()
    }
}