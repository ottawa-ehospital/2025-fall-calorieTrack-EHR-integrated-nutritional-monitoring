package com.example.calorieapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SessionManager {

    var currentProfile: PatientProfile? = null
    private val httpClient = OkHttpClient()
    private const val API_BASE_URL = "https://aetab8pjmb.us-east-1.awsapprunner.com/table"

    private val vitalsDateParser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // The API returns timestamps in UTC (Z-format)
    private val logTimestampParser: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    private var mealLogCache: List<MealLog>? = null


    /**
     * Fetches all patient data *after* login and builds the PatientProfile.
     * ★★★ UPDATED to fetch weight/height ★★★
     */
    suspend fun loadProfile(user: User) = withContext(Dispatchers.IO) {
        Log.d("SessionManager", "Starting profile load for user: ${user.userId}")
        try {
            val profile = coroutineScope {
                // 1. Fetch data from patients_registration
                val regDataDeferred = async { fetchRegistrationInfo(user.userId) }

                // 2. Fetch data from vitals_history
                val vitalsDataDeferred = async { fetchRecentVitals(user.userId) }

                // 3. Fetch data from bloodtests
                val bloodTestsDataDeferred = async { fetchBloodTests(user.userId) }

                // 4. Fetch data from allergy_records
                val allergiesDataDeferred = async { fetchAllergies(user.userId) }

                // 5. Fetch data from ai_diagnostics
                val diagnosticsDataDeferred = async { fetchDiagnostics(user.userId) }

                // Wait for all of them to complete (await)
                val registrationData = regDataDeferred.await()
                val vitalsData = vitalsDataDeferred.await()
                val bloodTestsData = bloodTestsDataDeferred.await()
                val allergiesData = allergiesDataDeferred.await()
                val diagnosticsData = diagnosticsDataDeferred.await()

                // We use isNull check before optDouble to correctly handle null vs 0.0
                val weight_kg = if (registrationData.isNull("weight_kg")) {
                    null
                } else {
                    registrationData.optDouble("weight_kg")
                }
                val height_cm = if (registrationData.isNull("height_cm")) {
                    null
                } else {
                    registrationData.optDouble("height_cm")
                }

                // Construct the final PatientProfile object
                PatientProfile(
                    user = user,
                    name = registrationData.optString("name", "N/A"),
                    dob = registrationData.optString("dob", "N/A"),
                    gender = registrationData.optString("gender", "N/A"),
                    weight_kg = weight_kg,
                    height_cm = height_cm,
                    recentVitals = vitalsData,
                    recentBloodTests = bloodTestsData,
                    allergies = allergiesData,
                    diagnosedConditions = diagnosticsData
                )
            }
            currentProfile = profile

            Log.d("SessionManager", "--- PATIENT PROFILE LOADED (Filtered) ---")
            Log.d("SessionManager", profile.toString())
            Log.d("SessionManager", "---------------------------------")

        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to load profile", e)
            currentProfile = null // Ensure profile is null on failure
        }
    }

    /**
     * Clears the session on logout.
     */
    fun clearSession() {
        currentProfile = null
        Log.d("SessionManager", "Session cleared.")
    }

    /**
     * Clears the meal log cache. Call this on logout or after posting a new meal.
     */
    fun clearMealLogCache() {
        mealLogCache = null
        Log.d("SessionManager", "Meal log cache cleared.")
    }
    /**
     * Robustly parses a date string from the API.
     */
    private fun parseDate(dateString: String): Date {
        if (dateString.isEmpty() || dateString == "null") {
            return Date() // Return "now" as a fallback
        }
        return try {
            // Try the standard Z-format first
            logTimestampParser.parse(dateString) ?: Date()
        } catch (e: ParseException) {
            try {
                // Fallback: Try the vitals format
                vitalsDateParser.parse(dateString) ?: Date()
            } catch (e2: ParseException) {
                Log.w("SessionManager", "Could not parse date: $dateString. Using current date.")
                Date() // Return "now" if all parsing fails
            }
        }
    }
    /**
     * Fetches the user's entire meal log, using a cache.
     */
    suspend fun getMealLog(patientId: Int): List<MealLog>? = withContext(Dispatchers.IO) {
        // 1. Return cached data if available
        if (mealLogCache != null) {
            Log.d("SessionManager", "Returning cached meal log")
            return@withContext mealLogCache
        }

        Log.d("SessionManager", "Fetching new meal log from API...")
        val url = "$API_BASE_URL/app_nutrition_log?patient_id=eq.$patientId"
        val logList = mutableListOf<MealLog>()

        try {
            val jsonArray = makeApiCall(url)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)

                if (json.optInt("patient_id") != patientId) continue

                val meal = MealLog(
                    logId = json.optInt("log_id"),
                    patientId = json.optInt("patient_id"),
                    loggedAt = parseDate(json.optString("logged_at")),
                    detectedDish = json.optString("identified_foods", null),
                    identifiedFoods = json.optString("ingredients_list", ""),
                    estimatedPortions = json.optString("estimated_portions"),
                    calories = json.optDouble("calories", 0.0),
                    protein = json.optDouble("protein_g", 0.0),
                    fat = json.optDouble("fat_g", 0.0),
                    carbs = json.optDouble("carbohydrates_g", 0.0),
                    sodium = json.optDouble("sodium_mg", 0.0),
                    sugar = json.optDouble("sugar_g", 0.0),
                    risk = json.optString("insight_risk", null),
                    warning = json.optString("insight_warning", null),
                    positive = json.optString("insight_positive", null)
                )
                logList.add(meal)
            }

            Log.d("SessionManager", "Fetched and parsed ${logList.size} meal logs.")
            mealLogCache = logList // 2. Save to cache
            return@withContext logList

        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to fetch meal log", e)
            return@withContext null // Return null on failure
        }
    }
    @Throws(IOException::class, IllegalStateException::class)
    private fun fetchRegistrationInfo(patientId: Int): JSONObject {
        // We still ask the API to filter, even if it's ignoring it.
        val url = "$API_BASE_URL/patients_registration?patient_id=eq.$patientId"
        Log.d("SessionManager", "Fetching: $url")
        val jsonArray = makeApiCall(url)

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            if (json.optInt("patient_id") == patientId) {
                Log.d("SessionManager", "Found matching registration info.")
                return json // Return the first *correct* match
            }
        }

        Log.w("SessionManager", "No registration info found for patient $patientId")
        return JSONObject() // Return empty object if no data
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun fetchRecentVitals(patientId: Int): Vitals {
        val url = "$API_BASE_URL/vitals_history?patient_id=eq.$patientId"
        Log.d("SessionManager", "Fetching: $url")
        val jsonArray = makeApiCall(url)
        val vitalsList = mutableListOf<JSONObject>()

        // 1. Get ALL vitals that *actually* match our patientId
        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            if (json.optInt("patient_id") == patientId) {
                vitalsList.add(json)
            }
        }

        Log.d("SessionManager", "Found ${vitalsList.size} total vitals records for patient $patientId.")

        val mostRecentVital = vitalsList.maxByOrNull { item ->
            val dateString = item.optString("recorded_on")
            try {
                vitalsDateParser.parse(dateString)?.time ?: 0L
            } catch (e: ParseException) {
                0L
            }
        }

        if (mostRecentVital != null) {
            Log.d("SessionManager", "Found most recent vital: ${mostRecentVital.optString("recorded_on")}")
            return Vitals(
                bloodPressure = mostRecentVital.optString("blood_pressure", "N/A"),
                heartRate = mostRecentVital.optInt("heart_rate", 0),
                temperature = mostRecentVital.optDouble("temperature", 0.0)
            )
        }

        Log.w("SessionManager", "No vitals found for patient $patientId")
        return Vitals("N/A", 0, 0.0) // Return default if no vitals
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun fetchBloodTests(patientId: Int): List<BloodTest> {
        val url = "$API_BASE_URL/bloodtests?patient_id=eq.$patientId"
        Log.d("SessionManager", "Fetching: $url")
        val jsonArray = makeApiCall(url)
        val testList = mutableListOf<BloodTest>()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            if (json.optInt("patient_id") == patientId) {
                testList.add(
                    BloodTest(
                        testName = json.optString("test_name"),
                        resultValue = json.optString("result_value"),
                        unit = json.optString("unit"),
                        normalRange = json.optString("normal_range"),
                        testDate = json.optString("test_date")
                    )
                )
            }
        }
        Log.d("SessionManager", "Found ${testList.size} *matching* blood tests for patient $patientId")
        return testList
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun fetchAllergies(patientId: Int): List<Allergy> {
        val url = "$API_BASE_URL/allergy_records?patient_id=eq.$patientId"
        Log.d("SessionManager", "Fetching: $url")
        val jsonArray = makeApiCall(url)
        val allergyList = mutableListOf<Allergy>()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            if (json.optInt("patient_id") == patientId) {
                allergyList.add(
                    Allergy(
                        allergen = json.optString("allergen"),
                        severity = json.optString("severity")
                    )
                )
            }
        }
        Log.d("SessionManager", "Found ${allergyList.size} *matching* allergies for patient $patientId")
        return allergyList
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun fetchDiagnostics(patientId: Int): List<String> {
        val url = "$API_BASE_URL/ai_diagnostics?patient_id=eq.$patientId"
        Log.d("SessionManager", "Fetching: $url")
        val jsonArray = makeApiCall(url)
        val conditionsList = mutableListOf<String>()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            if (json.optInt("patient_id") == patientId) {
                conditionsList.add(json.optString("disease_type", "Unknown"))
            }
        }
        Log.d("SessionManager", "Found ${conditionsList.size} *matching* diagnosed conditions for patient $patientId")
        return conditionsList
    }

    @Throws(IOException::class, IllegalStateException::class)
    private fun makeApiCall(url: String): JSONArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("API Call Failed: ${response.code} $url")
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty response body from $url")

            // Handle API responses that might be a single object instead of an array
            // This is a common issue with a misconfigured API
            if (responseBody.startsWith("{")) {
                val jsonResponse = JSONObject(responseBody)
                return jsonResponse.optJSONArray("data") ?: JSONArray()
            } else if (responseBody.startsWith("[")) {
                // The API might be returning a raw array, which is bad practice but possible
                Log.w("SessionManager", "API returned a raw JSON array for $url. This is non-standard.")
                return JSONArray(responseBody)
            }

            Log.e("SessionManager", "Invalid JSON response from $url: $responseBody")
            return JSONArray()
        }
    }
}