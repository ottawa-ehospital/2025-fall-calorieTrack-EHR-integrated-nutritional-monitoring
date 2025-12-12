package com.example.calorieapp

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class MealDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEAL_LOG = "extra_meal_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_detail)

        val backButton = findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener { finish() }

        // Get the MealLog object from the Intent
        val mealLog = intent.getSerializableExtra(EXTRA_MEAL_LOG) as? MealLog

        if (mealLog != null) {
            displayMealDetails(mealLog)
        } else {
            // Handle missing data gracefully
            findViewById<TextView>(R.id.detail_dish_name).text = "Error: Meal Data Not Found"
            findViewById<TextView>(R.id.detail_date).visibility = View.GONE
        }
    }

    private fun displayMealDetails(meal: MealLog) {
        // 1. Header Info
        val dishNameText = findViewById<TextView>(R.id.detail_dish_name)
        val dateText = findViewById<TextView>(R.id.detail_date)
        val portionText = findViewById<TextView>(R.id.detail_portion)
        val ingredientsText = findViewById<TextView>(R.id.detail_ingredients)

        val displayName = if (!meal.detectedDish.isNullOrEmpty()) meal.detectedDish else "Logged Meal"
        dishNameText.text = displayName.replaceFirstChar { it.titlecase() }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.US)
        dateText.text = dateFormat.format(meal.loggedAt)

        portionText.text = if (meal.estimatedPortions.isNotEmpty()) meal.estimatedPortions else "N/A"
        ingredientsText.text = if (meal.identifiedFoods.isNotEmpty()) meal.identifiedFoods.replace(",", ", ") else "N/A"

        // 2. Nutrition Info
        findViewById<TextView>(R.id.detail_calories).text = "${meal.calories.toInt()}"
        findViewById<TextView>(R.id.detail_protein).text = "${meal.protein.toInt()}g"
        findViewById<TextView>(R.id.detail_carbs).text = "${meal.carbs.toInt()}g"
        findViewById<TextView>(R.id.detail_fat).text = "${meal.fat.toInt()}g"

        // 3. Insights (Visibility Logic)
        val cardRisk = findViewById<MaterialCardView>(R.id.card_risk)
        val textRisk = findViewById<TextView>(R.id.text_risk)

        val cardWarning = findViewById<MaterialCardView>(R.id.card_warning)
        val textWarning = findViewById<TextView>(R.id.text_warning)

        val cardPositive = findViewById<MaterialCardView>(R.id.card_positive)
        val textPositive = findViewById<TextView>(R.id.text_positive)

        val textNoInsights = findViewById<TextView>(R.id.text_no_insights)

        var hasInsights = false

        // Risk
        if (!meal.risk.isNullOrEmpty()) {
            cardRisk.visibility = View.VISIBLE
            textRisk.text = formatInsightText("HIGH RISK", meal.risk)
            hasInsights = true
        } else {
            cardRisk.visibility = View.GONE
        }

        // Warning
        if (!meal.warning.isNullOrEmpty()) {
            cardWarning.visibility = View.VISIBLE
            textWarning.text = formatInsightText("WARNING", meal.warning)
            hasInsights = true
        } else {
            cardWarning.visibility = View.GONE
        }

        // Positive
        if (!meal.positive.isNullOrEmpty()) {
            cardPositive.visibility = View.VISIBLE
            textPositive.text = formatInsightText("POSITIVE", meal.positive)
            hasInsights = true
        } else {
            cardPositive.visibility = View.GONE
        }

        if (!hasInsights) {
            textNoInsights.visibility = View.VISIBLE
        }
    }

    private fun formatInsightText(prefix: String, text: String): String {
        // If the text already starts with the prefix (e.g. "HIGH RISK: ..."), just return it.
        if (text.uppercase().startsWith(prefix)) return text
        return "$prefix:\n$text"
    }
}