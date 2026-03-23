package com.eto.predictor

import android.content.Intent
import android.os.Bundle
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
import com.eto.predictor.databinding.ActivitySignupBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
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
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

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

        binding.btnSignup.setOnClickListener { signupWithEmail() }
        binding.btnGoogle.setOnClickListener { signInWithGoogle() }
        binding.tvGoLogin.setOnClickListener { finish() }
    }

    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE).getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
    }

    private fun signupWithEmail() {
        val name     = binding.etName.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirm  = binding.etConfirmPassword.text.toString().trim()

        if (name.isEmpty()) { showDialog("Missing Name", "Please enter your name."); return }
        if (email.isEmpty()) { showDialog("Missing Email", "Please enter your email."); return }
        if (password.isEmpty()) { showDialog("Missing Password", "Please enter a password."); return }
        if (password.length < 6) { showDialog("Weak Password", "Password must be at least 6 characters."); return }
        if (password != confirm) { showDialog("Password Mismatch", "Passwords do not match."); return }

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val profileUpdate = userProfileChangeRequest { displayName = name }
                    auth.currentUser?.updateProfile(profileUpdate)
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener {
                            setLoading(false)
                            showDialog(
                                "Verify Your Email",
                                "A verification link has been sent to $email. Please verify before logging in."
                            )
                            auth.signOut()
                            finish()
                        }
                } else {
                    setLoading(false)
                    showDialog("Signup Failed", task.exception?.message ?: "Please try again.")
                }
            }
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
                val result = credentialManager.getCredential(this@SignupActivity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            setLoading(false)
                            if (task.isSuccessful) {
                                startActivity(Intent(this@SignupActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                                finish()
                            } else {
                                showDialog("Google Sign-In Failed", task.exception?.message ?: "Please try again.")
                            }
                        }
                } else {
                    setLoading(false)
                    showDialog("Error", "Unexpected credential type.")
                }
            } catch (e: GetCredentialException) {
                setLoading(false)
                showDialog("Google Sign-In Failed", e.message ?: "Please try again.")
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

    private fun setLoading(loading: Boolean) {
        binding.progressSignup.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignup.isEnabled = !loading
        binding.btnGoogle.isEnabled = !loading
    }
}
