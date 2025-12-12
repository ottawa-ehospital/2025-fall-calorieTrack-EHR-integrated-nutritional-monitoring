package com.example.calorieapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MealLogAdapter(
    private var mealLogs: List<MealLog>,
    private val onItemClicked: (MealLog) -> Unit
) : RecyclerView.Adapter<MealLogAdapter.MealViewHolder>() {

    // Date formatter
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.meal_log_item, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        val mealLog = mealLogs[position]
        holder.bind(mealLog)
    }

    override fun getItemCount(): Int = mealLogs.size

    fun updateData(newMealLogs: List<MealLog>) {
        this.mealLogs = newMealLogs.sortedByDescending { it.loggedAt }
        notifyDataSetChanged()
    }

    inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val foodNameText: TextView = itemView.findViewById(R.id.item_food_name)
        private val caloriesText: TextView = itemView.findViewById(R.id.item_calories)
        private val dateText: TextView = itemView.findViewById(R.id.item_log_date)
        private val nutrientsText: TextView = itemView.findViewById(R.id.item_nutrients)

        fun bind(mealLog: MealLog) {
            // 1. Set Data
            val foodTitle = if (!mealLog.detectedDish.isNullOrEmpty()) {
                mealLog.detectedDish
            } else {
                mealLog.identifiedFoods.split(",").firstOrNull() ?: "Logged Meal"
            }
            foodNameText.text = foodTitle.trim().replaceFirstChar { it.titlecase() }
            caloriesText.text = "${mealLog.calories.toInt()} kcal"
            dateText.text = dateFormatter.format(mealLog.loggedAt)

            // Sodium removed from display
            val nutrientsString = "P: ${mealLog.protein.toInt()}g   •   C: ${mealLog.carbs.toInt()}g   •   F: ${mealLog.fat.toInt()}g"
            nutrientsText.text = nutrientsString

            // 2. Set Click Listener (New)
            itemView.setOnClickListener {
                onItemClicked(mealLog)
            }
        }
    }
}