package com.example.seatfinder.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.seatfinder.MainActivity
import com.example.seatfinder.R
import kotlinx.coroutines.launch
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalConfiguration

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immagine a tutto schermo, sotto la status bar
        enableEdgeToEdge()

        // Se la sessione è ancora valida, salta il login
        if (AuthManager.restoreSession(this)) {
            goToMain()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary        = Color(0xFF0A5B95),
                    onPrimary      = Color.White,
                    primaryContainer = Color(0xFF0A5B95),
                    onPrimaryContainer = Color.White,
                    secondary      = Color(0xFF0A5B95),
                )
            ) {
                LoginScreen(onLoginSuccess = { goToMain() })
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var matricola by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val canLogin = matricola.isNotBlank() && password.isNotBlank() && !loading

    // Altezza schermo: NON cambia quando appare la tastiera
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor = Color.White
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Sfondo fisso
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Posizione fissa: porta i campi nella zona bianca (regola 0.46f a gusto)
            Spacer(Modifier.height(screenHeight * 0.46f))

            OutlinedTextField(
                value = matricola,
                onValueChange = { matricola = it },
                label = { Text("Matricola") },
                singleLine = true,
                enabled = !loading,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !loading,
                colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        val result = EsseTreAuthApi.login(
                            matricola.trim(),
                            password.trim()
                        )
                        loading = false

                        when (result) {
                            is LoginResult.Success -> {
                                AuthManager.saveSession(context, result.user)
                                onLoginSuccess()
                            }
                            LoginResult.InvalidCredentials ->
                                Toast.makeText(
                                    context,
                                    "Matricola o password errata",
                                    Toast.LENGTH_SHORT
                                ).show()
                            is LoginResult.Error ->
                                Toast.makeText(
                                    context,
                                    "Impossibile accedere, controlla la connessione",
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                },
                enabled = canLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0A5B95),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Login")
            }

            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF0A5B95))
            }

            Spacer(Modifier.height(32.dp)) // margine in fondo per lo scroll
        }
    }
}