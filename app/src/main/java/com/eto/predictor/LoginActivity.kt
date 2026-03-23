package com.eto.predictor

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.eto.predictor.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("eto_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        // Skip login if already verified
        val current = auth.currentUser
        if (current != null && current.isEmailVerified) {
            goToMain(); return
        }

        // ── Theme toggle ──────────────────────────────────────────
        updateThemeIcon()
        binding.btnThemeToggle.setOnClickListener {
            val p = getSharedPreferences("eto_prefs", MODE_PRIVATE)
            val newDark = !p.getBoolean("is_dark", false)
            p.edit { putBoolean("is_dark", newDark) }
            AppCompatDelegate.setDefaultNightMode(
                if (newDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.btnLogin.setOnClickListener { loginWithEmail() }
        binding.btnGoogle.setOnClickListener { signInWithGoogle() }
        binding.tvGoSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener { sendPasswordReset() }
        binding.btnResendVerification.setOnClickListener {
            auth.currentUser?.sendEmailVerification()
                ?.addOnCompleteListener {
                    Toast.makeText(this, "Verification email resent!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE).getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
    }

    private fun loginWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() && password.isEmpty()) {
            showDialog("Missing Fields", "Please enter both your email and password.")
            return
        }
        if (email.isEmpty()) {
            showDialog("Missing Email", "Please enter your email address.")
            return
        }
        if (password.isEmpty()) {
            showDialog("Missing Password", "Please enter your password.")
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.isEmailVerified == true) {
                        goToMain()
                    } else {
                        // Signed in but email not verified
                        showDialog(
                            "Email Not Verified",
                            "Your email has not been verified yet. Please check your inbox and click the verification link, then try logging in again."
                        )
                        binding.btnResendVerification.visibility = View.VISIBLE
                        // Sign out so they can't bypass verification
                        auth.signOut()
                    }
                } else {
                    val error = task.exception
                    val title: String
                    val message: String

                    when (error) {
                        is FirebaseAuthInvalidUserException -> {
                            title = "Email Not Found"
                            message = "No account exists with \"$email\". Please check your email or sign up."
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            // This exception covers BOTH bad email format AND wrong password
                            val errCode = error.errorCode
                            if (errCode == "ERROR_INVALID_EMAIL") {
                                title = "Invalid Email"
                                message = "The email address format is invalid. Please check and try again."
                            } else {
                                // ERROR_WRONG_PASSWORD or ERROR_INVALID_CREDENTIAL
                                title = "Wrong Password"
                                message = "The password you entered is incorrect. Please try again or tap \"Forgot Password\" to reset it."
                            }
                        }
                        else -> {
                            title = "Login Failed"
                            message = error?.message ?: "Something went wrong. Please try again."
                        }
                    }
                    showDialog(title, message)
                }
            }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun signInWithGoogle() {
        setLoading(true)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential =
                        GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            setLoading(false)
                            if (task.isSuccessful) goToMain()
                            else showDialog(
                                "Google Sign-In Failed",
                                task.exception?.message ?: "Please try again."
                            )
                        }
                } else {
                    setLoading(false)
                    showDialog("Error", "Unexpected credential type. Please try again.")
                }
            } catch (e: GetCredentialException) {
                setLoading(false)
                showDialog("Google Sign-In Failed", e.message ?: "Please try again.")
            }
        }
    }

    private fun sendPasswordReset() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            showDialog("Missing Email", "Please enter your email address in the field above first.")
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful)
                    showDialog(
                        "Reset Email Sent",
                        "A password reset link has been sent to $email. Please check your inbox."
                    )
                else
                    showDialog("Error", task.exception?.message ?: "Failed to send reset email.")
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogle.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
