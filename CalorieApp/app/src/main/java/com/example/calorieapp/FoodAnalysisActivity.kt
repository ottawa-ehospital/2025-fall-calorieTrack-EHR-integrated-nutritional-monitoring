package com.example.calorieapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoodAnalysisActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var backButton: ImageButton
    private lateinit var dishInfoCard: MaterialCardView
    private lateinit var detectedDishText: TextView
    private lateinit var portionSizeText: TextView
    private lateinit var detectedItemsText: TextView
    private lateinit var nutritionalBreakdownText: TextView
    private lateinit var logMealButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var finalVerdictCard: MaterialCardView
    private lateinit var finalVerdictTitle: TextView
    private lateinit var finalVerdictReasoning: TextView
    private lateinit var insightDetailsTitle: TextView
    private lateinit var insight1Card: MaterialCardView // Risks
    private lateinit var insight2Card: MaterialCardView // Warnings
    private lateinit var insight3Card: MaterialCardView // Positives
    private lateinit var insight1Text: TextView // Risks
    private lateinit var insight2Text: TextView // Warnings
    private lateinit var insight3Text: TextView // Positives

    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    private val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY
    private val LOG_MEAL_API_URL = "https://aetab8pjmb.us-east-1.awsapprunner.com/table/app_nutrition_log"

    // To store the final result
    private var aiResponse: OpenAiFoodResponse? = null

    // --- Constants for scoring ---
    private val SCORE_HIGH_RISK = -10 // Score per risk
    private val SCORE_WARNING = -3   // Score per warning
    private val SCORE_POSITIVE = 2   // Score per positive point

    // Data class for our final verdict
    data class FinalVerdict(
        val title: String,
        val reasoning: String,
        val colorRes: Int
    )

    companion object {
        const val EXTRA_IMAGE_URI = "com.example.calorieapp.IMAGE_URI"
        const val EXTRA_USER_HINT = "com.example.calorieapp.USER_HINT"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.food_analysis)

        // Initialize UI Elements
        backButton = findViewById(R.id.back_button)
        dishInfoCard = findViewById(R.id.dish_info_card)
        detectedDishText = findViewById(R.id.detected_dish_text)
        portionSizeText = findViewById(R.id.portion_size_text)
        detectedItemsText = findViewById(R.id.detected_ingredients_text)
        nutritionalBreakdownText = findViewById(R.id.nutritional_breakdown_text)
        logMealButton = findViewById(R.id.log_meal_button)
        progressBar = findViewById(R.id.progressBar)
        bottomNavView = findViewById(R.id.bottom_navigation)

        // Initialize Final Verdict UI
        finalVerdictCard = findViewById(R.id.final_verdict_card)
        finalVerdictTitle = findViewById(R.id.final_verdict_title)
        finalVerdictReasoning = findViewById(R.id.final_verdict_reasoning)

        // Initialize Insight Details UI
        insightDetailsTitle = findViewById(R.id.insights_details_title)
        insight1Card = findViewById(R.id.insight_1_card)
        insight2Card = findViewById(R.id.insight_2_card)
        insight3Card = findViewById(R.id.insight_3_card)
        insight1Text = findViewById(R.id.insight_1)
        insight2Text = findViewById(R.id.insight_2)
        insight3Text = findViewById(R.id.insight_3)

        // Set up Listeners
        backButton.setOnClickListener {
            val intent = Intent(this, FoodScanActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
            startActivity(intent, options.toBundle())
        }

        logMealButton.setOnClickListener {
            logMealToDatabase()
        }

        setupBottomNavigation()
        processImage(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processImage(intent)
    }

    private fun setupBottomNavigation() {
        bottomNavView.selectedItemId = R.id.nav_camera
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
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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
        bottomNavView.selectedItemId = R.id.nav_camera
    }

    private fun processImage(intent: Intent?) {
        val imageUriString = intent?.getStringExtra(EXTRA_IMAGE_URI)
        val userHint = intent?.getStringExtra(EXTRA_USER_HINT)

        if (imageUriString == null) {
            showError("No image URI provided")
        } else {
            val imageUri = Uri.parse(imageUriString)
            val bitmap = uriToBitmap(imageUri)

            if (bitmap != null) {
                if (SessionManager.currentProfile == null) {
                    showError("Error: Patient profile not loaded. Please log out and log in again.")
                } else {
                    analyzeImage(bitmap, userHint)
                }
            } else {
                showError("Could not load image")
            }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("ImageConversion", "Error converting URI to Bitmap", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun analyzeImage(bitmap: Bitmap, userHint: String?) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            detectedItemsText.text = ""
            nutritionalBreakdownText.text = ""
            detectedDishText.text = ""
            portionSizeText.text = ""

            // Reset the button
            logMealButton.isEnabled = true
            logMealButton.visibility = View.VISIBLE // Ensure visible in case hidden by error
            logMealButton.text = "Log Meal"

            // Hide fields
            finalVerdictCard.visibility = View.GONE
            dishInfoCard.visibility = View.GONE
            insightDetailsTitle.visibility = View.GONE
            insight1Card.visibility = View.GONE
            insight2Card.visibility = View.GONE
            insight3Card.visibility = View.GONE

            try {
                val base64Image = bitmapToBase64(bitmap)
                val analysisResult = getOpenAiAnalysis(base64Image, userHint)

                aiResponse = analysisResult
                displayResults(analysisResult)

            } catch (e: IOException) {
                Log.e("OpenAI", "Network error: ${e.message}", e)
                showError("Network Error: Could not connect to API.")
            } catch (e: Exception) {
                Log.e("OpenAI", "Analysis error: ${e.message}", e)
                showError("Analysis Error: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun formatTestDate(dateString: String?): String {
        if (dateString.isNullOrEmpty() || dateString == "null") return "Unknown Date"
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
                dateString.split("T").firstOrNull() ?: "Unknown Date"
            }
        }
    }

    private fun buildJsonPayload(base64Image: String, userHint: String?): JSONObject {
        val profile = SessionManager.currentProfile!!

        val allergiesString = profile.allergies.joinToString(", ") { it.allergen }.ifEmpty { "none" }
        val diagnosedConditions = profile.diagnosedConditions.joinToString(", ").ifEmpty { "none" }
        val recentVitals = profile.recentVitals
        val vitalsString = "BP: ${recentVitals.bloodPressure}, HR: ${recentVitals.heartRate} bpm"
        val recentTests = profile.recentBloodTests.joinToString("\n") {
            "- ${it.testName}: ${it.resultValue} ${it.unit} (Test Date: ${formatTestDate(it.testDate)})"
        }.ifEmpty { "none" }

        val systemMessageContent = """
        You are an expert nutritionist AI for an e-hospital. Your task is to analyze an image of food and provide a detailed breakdown,
        cross-referencing the patient's Electronic Health Record (EHR).
        
        The user has the following health profile:
        - ALLERGY_LIST: [$allergiesString]
        - CONDITION_LIST: [$diagnosedConditions]
        - Latest Vitals: $vitalsString
        - Recent Blood Tests: $recentTests

        You MUST return the results *only* as a single, minified JSON object.
        Do not include any text, headers, or markdown formatting (like ```json) outside of the JSON object.
        
        The JSON object must strictly follow this exact format:
        {
          "dishName": "string",
          "portionSize": "string",
          "ingredients": ["string", "string", ...],
          "nutritionalBreakdown": {
            "totalCalories": 0.0,
            "totalProtein": 0.0,
            "totalFat": 0.0,
            "totalCarbs": 0.0,
            "totalSodium": 0.0,
            "totalSugar": 0.0
          },
          "insights": {
            "risks": [],
            "warnings": [],
            "positives": []
          }
        }
        
        RULES FOR 'dishName', 'portionSize', AND 'ingredients':
        1. "dishName": Identify the overall dish. (e.g., "Caesar Salad", "Pumpkin Soup", "Almonds").
        2. "portionSize": Estimate the total portion. (e.g., "1 large bowl (approx 400g)", "1 cup (approx 250ml)", "5 pieces (approx 10g)").
        3. "ingredients": List the *primary, actual ingredients* visible or strongly implied by the dish. (e.g., ["Romaine Lettuce", "Grilled Chicken", "Croutons", "Caesar Dressing"]).
           - For single-item foods (like "Almonds" or "Apple"), this array should be empty OR contain *only* the item name: ["Almonds"].
        4. NON-FOOD IMAGES: If the image does not contain any food items, you MUST set "dishName" to "NOT_FOOD".
           
        RULES FOR "insights" FIELDS (READ CAREFULLY):
        
        1. "risks":
           - **STEP 1:** Look at the `dishName` (e.g., "Almonds") and the `ingredients` list (e.g., ["Almonds"]).
           - **STEP 2:** Look at this *exact, complete, and final* ALLERGY_LIST: [$allergiesString].
           - **STEP 3:** Compare STEP 1 to STEP 2. A risk is *only* triggered if a word from STEP 1 *exactly* matches a word from STEP 2 (case-insensitive).
           - **ABSOLUTE_RULE**: Do NOT infer relationships. If the food is "Almonds" and the allergy is "Peanuts", they DO NOT MATCH. Do not flag it. If the food is "Soy Milk" and the allergy is "Milk", they DO NOT MATCH.
           - **STEP 4:** Only if an *exact* match is found, add the risk string. (e.g., Food: "Wheat Bread", Allergy List: ["Wheat"] -> "risks": ["HIGH RISK: Contains Wheat, which is on your ALLERGY_LIST."])
           - If no *exact* matches are found, return "risks": []

        2. "warnings":
           - First, look at the `nutritionalBreakdown` (e.g., "totalSodium": 800.0).
           - Second, compare this to the user's CONDITION_LIST: [$diagnosedConditions] and "Recent Blood Tests".
           - Third, add a "WARNING:" string for *every* nutritional value that is bad for those conditions.
           - Example: "warnings": ["WARNING: The high sodium (800mg) is a concern for your Stroke condition."]
           - If no warnings, return "warnings": []
           
        3. "positives":
           - List *all* significant positive nutritional facts. (e.g., "POSITIVE: This is an excellent source of protein!")
           - If AND ONLY IF the "risks" array AND the "warnings" array are both empty ([]), you MUST add one "NEUTRAL:" string to this array.
           - Example (if safe): "positives": ["POSITIVE: Good source of fiber.", "NEUTRAL: This food poses no immediate risks or warnings for your health profile."]
           - Example (if not safe): "positives": ["POSITIVE: Good source of fiber."]
           - If no positives and no risks/warnings, return "positives": ["NEUTRAL: This food poses no immediate risks or warnings for your health profile."]
        """.trimIndent()

        val hintString = if (userHint.isNullOrEmpty()) "No hint provided." else userHint
        val userMessageContent = """
            Please analyze this image and provide the complete JSON breakdown, considering my health profile.
            The user provided this hint: "$hintString"
        """.trimIndent()

        val payload = JSONObject()
        payload.put("model", "gpt-4o-mini")
        payload.put("response_format", JSONObject().put("type", "json_object"))

        val messagesArray = JSONArray()

        // 1. System Message
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", systemMessageContent)
        messagesArray.put(systemMessage)

        // 2. User Message
        val userContentArray = JSONArray()
        val textPart = JSONObject()
        textPart.put("type", "text")
        textPart.put("text", userMessageContent)
        userContentArray.put(textPart)

        val imagePart = JSONObject()
        imagePart.put("type", "image_url")
        val imageUrlObject = JSONObject()
        imageUrlObject.put("url", "data:image/jpeg;base64,$base64Image")
        imagePart.put("image_url", imageUrlObject)
        userContentArray.put(imagePart)

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", userContentArray)
        messagesArray.put(userMessage)

        payload.put("messages", messagesArray)
        payload.put("max_tokens", 2000)

        return payload
    }

    private suspend fun getOpenAiAnalysis(base64Image: String, userHint: String?): OpenAiFoodResponse = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonPayload(base64Image, userHint)
        val requestBody = jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string()
            if (!response.isSuccessful) {
                Log.e("OpenAI", "API Error: Code ${response.code}. Response: $responseBodyString")
                throw IOException("API Error ${response.code}: $responseBodyString")
            }

            if (responseBodyString == null) {
                throw IOException("Empty response body from API.")
            }

            val jsonResponse = JSONObject(responseBodyString)
            val contentString = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            return@withContext gson.fromJson(contentString, OpenAiFoodResponse::class.java)
        }
    }

    private fun calculateHealthScore(insights: PersonalizedInsights): Int {
        var score = 0
        var hasRealInsights = false

        if (insights.risks.isNotEmpty()) {
            score += (insights.risks.size * SCORE_HIGH_RISK)
            hasRealInsights = true
        }
        if (insights.warnings.isNotEmpty()) {
            score += (insights.warnings.size * SCORE_WARNING)
            hasRealInsights = true
        }
        if (insights.positives.isNotEmpty()) {
            val actualPositives = insights.positives.filterNot { it.startsWith("NEUTRAL:") }
            score += (actualPositives.size * SCORE_POSITIVE)
            hasRealInsights = true
        }

        if (score == 0 && hasRealInsights) return -1
        return score
    }

    private fun formatListForDisplay(list: List<String>): String {
        if (list.isEmpty()) return ""
        val details = list.joinToString("\n") { item ->
            val colonIndex = item.indexOf(":")
            var prefix = ""
            var detail = item.trim()

            if (colonIndex != -1) {
                prefix = item.substring(0, colonIndex + 1)
                detail = item.substring(colonIndex + 1).trim()
                "• $prefix $detail"
            } else {
                "• $detail"
            }
        }
        return details
    }

    private fun getFinalVerdict(score: Int): FinalVerdict {
        val hasHighRisks = aiResponse?.insights?.risks?.isNotEmpty() ?: false

        if (hasHighRisks) {
            return FinalVerdict(
                title = "Not Recommended",
                reasoning = "The high risks associated with this meal strongly outweigh any potential benefits.",
                colorRes = R.color.color_card_red
            )
        }

        return when {
            score < 0 -> FinalVerdict(
                title = "Consume in Moderation",
                reasoning = "This meal has drawbacks that conflict with your health profile. Please consider this a rare treat.",
                colorRes = R.color.color_card_yellow
            )
            score == 0 -> FinalVerdict(
                title = "Neutral Choice",
                reasoning = "This meal has no immediate risks for you, but it also does not offer significant health benefits.",
                colorRes = R.color.color_card_neutral
            )
            else -> FinalVerdict(
                title = "Recommended",
                reasoning = "This meal is a great choice and aligns well with your health profile and goals!",
                colorRes = R.color.color_card_green
            )
        }
    }

    private fun displayResults(response: OpenAiFoodResponse) {
        // ★★★ FIX: Check for "NOT_FOOD" or the placeholder "string" ★★★
        if (response.dishName.equals("NOT_FOOD", ignoreCase = true) ||
            response.dishName.equals("string", ignoreCase = true)) {
            showError("We could not detect any food in this image. Please try again.")
            return
        }

        // --- 1. GET THE SCORE AND VERDICT ---
        val score = calculateHealthScore(response.insights)
        val verdict = getFinalVerdict(score)

        val verdictColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(verdict.colorRes, theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(verdict.colorRes)
        }

        // --- 2. POPULATE THE FINAL VERDICT CARD ---
        finalVerdictCard.setCardBackgroundColor(verdictColor)
        finalVerdictTitle.text = verdict.title
        finalVerdictReasoning.text = verdict.reasoning
        finalVerdictCard.visibility = View.VISIBLE

        // --- 3. Populate Dish Info Card ---
        detectedDishText.text = response.dishName
        portionSizeText.text = response.portionSize
        dishInfoCard.visibility = View.VISIBLE

        // --- 4. Populate Detected Ingredients ---
        val ingredientsText = if (response.ingredients.isEmpty()) {
            "No specific ingredients listed."
        } else {
            response.ingredients.joinToString("\n") { "- $it" }
        }
        detectedItemsText.text = ingredientsText
        detectedItemsText.visibility = View.VISIBLE

        // --- 5. Populate Nutritional Breakdown (with formatting) ---
        val nutrients = response.nutritionalBreakdown
        val breakdownText = """
            Protein: ${String.format("%.1f", nutrients.totalProtein)}g
            Carbs: ${nutrients.totalCarbs.toInt()}g
            Fat: ${String.format("%.1f", nutrients.totalFat)}g
            Sodium: ${nutrients.totalSodium.toInt()}mg
            Sugar: ${nutrients.totalSugar.toInt()}g

            Calories: ${nutrients.totalCalories.toInt()} kcal
        """.trimIndent()
        nutritionalBreakdownText.text = breakdownText
        nutritionalBreakdownText.visibility = View.VISIBLE

        // --- 6. CONDITIONALLY SHOW INSIGHT DETAILS ---
        var insightsShown = 0

        if (response.insights.risks.isNotEmpty()) {
            insight1Text.text = formatListForDisplay(response.insights.risks)
            insight1Card.visibility = View.VISIBLE
            insightsShown++
        } else {
            insight1Card.visibility = View.GONE
        }

        if (response.insights.warnings.isNotEmpty()) {
            insight2Text.text = formatListForDisplay(response.insights.warnings)
            insight2Card.visibility = View.VISIBLE
            insightsShown++
        } else {
            insight2Card.visibility = View.GONE
        }

        if (response.insights.positives.isNotEmpty()) {
            insight3Text.text = formatListForDisplay(response.insights.positives)
            val hasOnlyNeutrals = response.insights.positives.all { it.startsWith("NEUTRAL:") }
            val colorRes = if (hasOnlyNeutrals) R.color.color_card_neutral else R.color.color_card_green
            val color = ContextCompat.getColor(this, colorRes)
            insight3Card.setCardBackgroundColor(color)
            insight3Card.visibility = View.VISIBLE
            insightsShown++
        } else {
            insight3Card.visibility = View.GONE
        }

        if (insightsShown > 0) {
            insightDetailsTitle.visibility = View.VISIBLE
        } else {
            insightDetailsTitle.visibility = View.GONE
        }
    }

    private fun logMealToDatabase() {
        if (aiResponse == null) {
            Toast.makeText(this, "No analysis data to log!", Toast.LENGTH_SHORT).show()
            return
        }

        val patientId = SessionManager.currentProfile?.user?.userId
        if (patientId == null) {
            Toast.makeText(this, "Error: No patient ID. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        logMealButton.isEnabled = false
        logMealButton.text = "Logging..."

        val payload = buildLogPayload(patientId, aiResponse!!)

        lifecycleScope.launch {
            try {
                val success = performMealLog(payload)
                if (success) {
                    Toast.makeText(this@FoodAnalysisActivity, "Meal Logged Successfully!", Toast.LENGTH_SHORT).show()
                    SessionManager.clearMealLogCache()
                    val intent = Intent(this@FoodAnalysisActivity, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    val options = ActivityOptionsCompat.makeCustomAnimation(this@FoodAnalysisActivity, 0, 0)
                    startActivity(intent, options.toBundle())
                } else {
                    Toast.makeText(this@FoodAnalysisActivity, "Failed to log meal to database.", Toast.LENGTH_LONG).show()
                    logMealButton.isEnabled = true
                    logMealButton.text = "Log Meal"
                }

            } catch (e: Exception) {
                Log.e("LogMeal", "Error logging meal", e)
                Toast.makeText(this@FoodAnalysisActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                logMealButton.isEnabled = true
                logMealButton.text = "Log Meal"
            }
        }
    }

    private fun buildLogPayload(patientId: Int, response: OpenAiFoodResponse): JSONObject {
        val payload = JSONObject()
        payload.put("patient_id", patientId)
        payload.put("image_storage_path", JSONObject.NULL)
        payload.put("identified_foods", response.dishName)
        payload.put("estimated_portions", response.portionSize)
        payload.put("ingredients_list", response.ingredients.joinToString(","))

        val nuts = response.nutritionalBreakdown
        payload.put("calories", nuts.totalCalories)
        payload.put("protein_g", nuts.totalProtein)
        payload.put("fat_g", nuts.totalFat)
        payload.put("carbohydrates_g", nuts.totalCarbs)
        payload.put("sodium_mg", nuts.totalSodium)
        payload.put("sugar_g", nuts.totalSugar)

        payload.put("insight_risk", response.insights.risks.joinToString("\n"))
        payload.put("insight_warning", response.insights.warnings.joinToString("\n"))
        val positivesOnly = response.insights.positives.filterNot { it.startsWith("NEUTRAL:") }
        payload.put("insight_positive", positivesOnly.joinToString("\n"))

        return payload
    }

    private suspend fun performMealLog(payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(LOG_MEAL_API_URL)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("LogMeal", "API Error: ${response.code} ${response.message}")
                    return@withContext false
                }
                return@withContext true
            }
        } catch (e: IOException) {
            Log.e("LogMeal", "Network IO Exception", e)
            return@withContext false
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE

        logMealButton.visibility = View.GONE

        finalVerdictTitle.text = "Analysis Error"
        finalVerdictReasoning.text = message

        val errorColor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                resources.getColor(R.color.color_card_red, theme)
            } else {
                @Suppress("DEPRECATION")
                resources.getColor(R.color.color_card_red)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                resources.getColor(android.R.color.holo_red_dark, theme)
            } else {
                @Suppress("DEPRECATION")
                resources.getColor(android.R.color.holo_red_dark)
            }
        }

        finalVerdictCard.setCardBackgroundColor(errorColor)
        finalVerdictCard.visibility = View.VISIBLE

        detectedItemsText.visibility = View.GONE
        nutritionalBreakdownText.visibility = View.GONE
        dishInfoCard.visibility = View.GONE
    }
}