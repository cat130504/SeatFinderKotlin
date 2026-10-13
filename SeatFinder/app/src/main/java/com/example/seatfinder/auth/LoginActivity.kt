package com.example.seatfinder.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.seatfinder.MainActivity
import com.example.seatfinder.R
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.restoreSession(this)) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtMatricola = findViewById<EditText>(R.id.txtMatricola)
        val txtPassword = findViewById<EditText>(R.id.txtPassword)
        val progressBar = findViewById<ProgressBar>(R.id.prLogin)

        fun updateButtonState() {
            btnLogin.isEnabled = txtMatricola.text.isNotBlank() && txtPassword.text.isNotBlank()
        }
        updateButtonState()
        txtMatricola.doAfterTextChanged { updateButtonState() }
        txtPassword.doAfterTextChanged { updateButtonState() }

        btnLogin.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                val result = EsseTreAuthApi.login(
                    txtMatricola.text.toString().trim(),
                    txtPassword.text.toString().trim()
                )
                progressBar.visibility = View.GONE
                updateButtonState()

                when (result) {
                    is LoginResult.Success -> {
                        AuthManager.saveSession(this@LoginActivity, result.user)
                        goToMain()
                    }
                    LoginResult.InvalidCredentials ->
                        toast("Matricola o password errata")
                    is LoginResult.Error ->
                        toast("Impossibile accedere, controlla la connessione")
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
