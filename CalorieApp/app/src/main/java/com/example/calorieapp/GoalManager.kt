package com.example.calorieapp

import android.content.Context
import android.content.SharedPreferences

object GoalManager {

    private const val PREFS_NAME = "CalorieTrackGoals"
    private lateinit var prefs: SharedPreferences

    private const val KEY_GOAL_CALORIES = "GOAL_CALORIES"
    private const val KEY_GOAL_PROTEIN = "GOAL_PROTEIN"
    private const val KEY_GOAL_CARBS = "GOAL_CARBS"
    private const val KEY_GOAL_FAT = "GOAL_FAT"

    val DEFAULT_GOALS = mapOf(
        KEY_GOAL_CALORIES to 2000,
        KEY_GOAL_PROTEIN to 120,
        KEY_GOAL_CARBS to 250,
        KEY_GOAL_FAT to 70
    )

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveGoals(calories: Int?, protein: Int?, carbs: Int?, fat: Int?) {
        val editor = prefs.edit()
        editor.putInt(KEY_GOAL_CALORIES, calories ?: -1)
        editor.putInt(KEY_GOAL_PROTEIN, protein ?: -1)
        editor.putInt(KEY_GOAL_CARBS, carbs ?: -1)
        editor.putInt(KEY_GOAL_FAT, fat ?: -1)
        editor.apply()
    }

    fun getGoalCalories(): Int {
        val saved = prefs.getInt(KEY_GOAL_CALORIES, -1)
        return if (saved == -1) DEFAULT_GOALS[KEY_GOAL_CALORIES]!! else saved
    }

    fun getGoalProtein(): Int {
        val saved = prefs.getInt(KEY_GOAL_PROTEIN, -1)
        return if (saved == -1) DEFAULT_GOALS[KEY_GOAL_PROTEIN]!! else saved
    }

    fun getGoalCarbs(): Int {
        val saved = prefs.getInt(KEY_GOAL_CARBS, -1)
        return if (saved == -1) DEFAULT_GOALS[KEY_GOAL_CARBS]!! else saved
    }

    fun getGoalFat(): Int {
        val saved = prefs.getInt(KEY_GOAL_FAT, -1)
        return if (saved == -1) DEFAULT_GOALS[KEY_GOAL_FAT]!! else saved
    }
}