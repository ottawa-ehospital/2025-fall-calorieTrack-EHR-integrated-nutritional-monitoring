package com.example.calorieapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MealLogActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var mealLogRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var mealLogAdapter: MealLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_log)

        backButton = findViewById(R.id.back_button)
        mealLogRecyclerView = findViewById(R.id.meal_log_recycler_view)
        progressBar = findViewById(R.id.meal_log_progress_bar)
        emptyStateText = findViewById(R.id.empty_state_text)

        setupRecyclerView()

        backButton.setOnClickListener {
            finish()
        }

        loadMealLog()
    }

    private fun setupRecyclerView() {
        // Initialize adapter with a click listener lambda
        mealLogAdapter = MealLogAdapter(emptyList()) { clickedMeal ->
            // This code runs when an item is clicked
            val intent = Intent(this, MealDetailActivity::class.java)
            intent.putExtra(MealDetailActivity.EXTRA_MEAL_LOG, clickedMeal)
            startActivity(intent)
        }

        mealLogRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MealLogActivity)
            adapter = mealLogAdapter
        }
    }

    private fun loadMealLog() {
        progressBar.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        mealLogRecyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val userId = SessionManager.currentProfile?.user?.userId ?: 0
            val log = SessionManager.getMealLog(userId)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (log.isNullOrEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                    mealLogRecyclerView.visibility = View.GONE
                } else {
                    mealLogAdapter.updateData(log)
                    emptyStateText.visibility = View.GONE
                    mealLogRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}