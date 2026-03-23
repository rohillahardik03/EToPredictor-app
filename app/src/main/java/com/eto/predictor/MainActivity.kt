package com.eto.predictor

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import com.eto.predictor.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("eto_prefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("is_dark", false)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // ── Toolbar ───────────────────────────────────────────────
        setSupportActionBar(binding.toolbar)

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }


        // ── Theme toggle top-right ────────────────────────────────
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

        // ── Nav drawer header ─────────────────────────────────────
        val headerView = binding.navigationView.getHeaderView(0)
        val user = auth.currentUser
        headerView.findViewById<TextView>(R.id.navHeaderEmail).text =
            user?.email ?: "Not logged in"
        headerView.findViewById<TextView>(R.id.navHeaderName).text =
            user?.displayName?.takeIf { it.isNotEmpty() } ?: "User"

        binding.navigationView.setNavigationItemSelectedListener(this)

        // ── Main buttons ──────────────────────────────────────────
        binding.btnManualInput.setOnClickListener {
            startActivity(Intent(this, ParameterSelectActivity::class.java))
        }
        binding.btnGpsInput.setOnClickListener {
            val intent = Intent(this, PredictActivity::class.java)
            intent.putExtra("mode", "auto")
            startActivity(intent)
        }
    }

    private fun updateThemeIcon() {
        val isDark = getSharedPreferences("eto_prefs", MODE_PRIVATE).getBoolean("is_dark", false)
        binding.btnThemeToggle.setImageResource(
            if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.nav_home -> { /* already on home */ }
            R.id.nav_edit_name -> showEditNameDialog()
            R.id.nav_change_password -> showChangePasswordDialog()
            R.id.nav_logout -> showLogoutDialog()
        }
        return true
    }

    private fun showEditNameDialog() {
        val layout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Display Name"
            setPadding(48, 16, 48, 0)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(auth.currentUser?.displayName ?: "")
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Edit Display Name")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profileUpdate = com.google.firebase.auth.userProfileChangeRequest {
                    displayName = newName
                }
                auth.currentUser?.updateProfile(profileUpdate)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val headerView = binding.navigationView.getHeaderView(0)
                            headerView.findViewById<TextView>(R.id.navHeaderName).text = newName
                            Toast.makeText(this, "Name updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showChangePasswordDialog() {
        val user = auth.currentUser
        val isGoogleUser = user?.providerData?.any { it.providerId == "google.com" } == true
        if (isGoogleUser) {
            AlertDialog.Builder(this)
                .setTitle("Not Supported")
                .setMessage("Google accounts manage passwords through Google. Please visit myaccount.google.com to change your password.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val currentPassInput = EditText(this).apply {
            hint = "Current Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPassInput = EditText(this).apply {
            hint = "New Password (min 6 chars)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(currentPassInput)
            addView(newPassInput)
        }

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val currentPass = currentPassInput.text.toString()
                val newPass = newPassInput.text.toString()
                if (currentPass.isEmpty() || newPass.isEmpty()) {
                    Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(
                        this,
                        "New password must be at least 6 characters",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val credential = EmailAuthProvider.getCredential(user?.email!!, currentPass)
                user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                    if (reAuthTask.isSuccessful) {
                        user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful)
                                AlertDialog.Builder(this)
                                    .setTitle("Success")
                                    .setMessage("Password updated successfully!")
                                    .setPositiveButton("OK", null)
                                    .show()
                            else
                                Toast.makeText(
                                    this,
                                    "Update failed: ${updateTask.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Wrong Password")
                            .setMessage("The current password you entered is incorrect.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
