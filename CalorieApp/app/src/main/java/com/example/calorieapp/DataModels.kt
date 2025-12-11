package com.example.calorieapp

import java.io.Serializable
import java.util.Date

data class OpenAiFoodResponse(
    val dishName: String,
    val portionSize: String,
    val ingredients: List<String>,
    val nutritionalBreakdown: NutritionalBreakdown,
    val insights: PersonalizedInsights
)

data class NutritionalBreakdown(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalFat: Double,
    val totalCarbs: Double,
    val totalSodium: Double,
    val totalSugar: Double
)

data class PersonalizedInsights(
    val risks: List<String>,
    val warnings: List<String>,
    val positives: List<String>
)

data class PatientProfile(
    val user: User,
    val name: String,
    val dob: String,
    val gender: String,
    val weight_kg: Double?,
    val height_cm: Double?,
    val recentVitals: Vitals,
    val recentBloodTests: List<BloodTest>,
    val allergies: List<Allergy>,
    val diagnosedConditions: List<String>
) {
    override fun toString(): String {
        return "PatientProfile(name='$name')"
    }
}

data class User(
    val userId: Int,
    val email: String,
    val username: String
)

data class DailySummary(
    var calories: Double = 0.0,
    var protein: Double = 0.0,
    var carbs: Double = 0.0,
    var fat: Double = 0.0,
    var sodium: Double = 0.0
)

/**
 * Represents a single logged meal from the 'app_nutrition_log' table.
 * ★★★ ADDED Serializable to allow passing via Intent ★★★
 */
data class MealLog(
    val logId: Int,
    val patientId: Int,
    val loggedAt: Date,
    val detectedDish: String?,
    val identifiedFoods: String,
    val estimatedPortions: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val sodium: Double,
    val sugar: Double,
    val risk: String?,
    val warning: String?,
    val positive: String?
) : Serializable

data class Vitals(
    val bloodPressure: String,
    val heartRate: Int,
    val temperature: Double
)

data class BloodTest(
    val testName: String,
    val resultValue: String,
    val unit: String,
    val normalRange: String,
    val testDate: String
)

data class Allergy(
    val allergen: String,
    val severity: String
)