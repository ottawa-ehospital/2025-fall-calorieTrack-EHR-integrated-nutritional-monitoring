package com.example.calorieapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var statusTextView: TextView

    private val httpClient = OkHttpClient()
    private val LOGIN_API_URL = "https://aetab8pjmb.us-east-1.awsapprunner.com/table/users"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_page)

        // Initialize the GoalManager with app context
        GoalManager.init(this)

        usernameEditText = findViewById(R.id.edit_text_username)
        passwordEditText = findViewById(R.id.edit_text_password)
        loginButton = findViewById(R.id.button_login)
        statusTextView = findViewById(R.id.text_status)

        loginButton.setOnClickListener {
            handleLogin()
        }
        statusTextView.text = ""
    }

    private fun handleLogin() {
        statusTextView.text = ""
        val email = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            statusTextView.text = "Email and password cannot be empty."
            return
        }

        loginButton.isEnabled = false
        statusTextView.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
        statusTextView.text = "Attempting login..."

        lifecycleScope.launch {
            try {
                // This function now fetches all users and finds a match
                val loggedInUser = performNetworkLogin(email, password)
                if (loggedInUser != null) {
                    statusTextView.text = "User found. Fetching profile..."

                    // Now that we have the User, fetch all their data
                    SessionManager.loadProfile(loggedInUser)

                    // Clear any old data from a previous user
                    SessionManager.clearMealLogCache()

                    withContext(Dispatchers.Main) {
                        // Check if the profile load was successful
                        val profile = SessionManager.currentProfile
                        if (profile != null) {
                            statusTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
                            statusTextView.text = "Login Successful! Redirecting..."
                            // Pass the REAL name from the profile
                            navigateToHomeScreen(profile.name)
                        } else {
                            // This means login was OK, but fetching profile data failed
                            statusTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                            statusTextView.text = "Login successful, but failed to load patient data."
                            loginButton.isEnabled = true
                        }
                    }
                } else {
                    // This means no user matched the email/password
                    withContext(Dispatchers.Main) {
                        statusTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                        statusTextView.text = "Login Failed. Invalid email or password."
                        loginButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginApp", "Login Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    statusTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                    statusTextView.text = "Connection Error. Could not reach API."
                    loginButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Fetches all users, finds a match, and returns a User object.
     * This is insecure but matches the request to check the full JSON.
     */
    private suspend fun performNetworkLogin(email: String, password: String): User? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LOGIN_API_URL).build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("LoginApp", "Login API Failed: Status ${response.code}")
                    return@withContext null
                }

                val responseBodyString = response.body?.string()
                if (responseBodyString == null) {
                    Log.e("LoginApp", "Empty response body.")
                    return@withContext null
                }

                val jsonResponse = JSONObject(responseBodyString)
                val usersArray = jsonResponse.optJSONArray("data")
                if (usersArray == null) {
                    Log.e("LoginApp", "No 'data' array in response.")
                    return@withContext null
                }

                // Loop through all users to find a match
                for (i in 0 until usersArray.length()) {
                    val userObject = usersArray.getJSONObject(i)
                    val userEmail = userObject.optString("email")
                    val userPasswordHash = userObject.optString("password_hash")

                    // Check for a match
                    if (userEmail.equals(email, ignoreCase = true) && userPasswordHash == password) {
                        // Found a match!
                        Log.d("LoginApp", "User match found!")
                        return@withContext User(
                            userId = userObject.getInt("user_id"), // <-- This is the patient_id
                            email = userEmail,
                            username = userObject.optString("username", "user")
                        )
                    }
                }

                // No match found after looping
                Log.w("LoginApp", "No user found matching credentials.")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("LoginApp", "Exception in performNetworkLogin", e)
            throw e // Re-throw to be caught by handleLogin
        }
    }

    /**
     * Navigates to HomeActivity and passes the user's name.
     */
    private fun navigateToHomeScreen(name: String) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_FIRST_NAME, name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}