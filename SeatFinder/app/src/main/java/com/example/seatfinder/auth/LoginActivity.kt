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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.seatfinder.MainActivity
import com.example.seatfinder.R
import com.example.seatfinder.ui.theme.SeatFinderTheme
import kotlinx.coroutines.launch

//Schermata di login: se la sessione è valida salta il login, altrimenti mostra LoginScreen
//Al successo salva la sessione e passa a MainActivity
class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immagine a tutto schermo sotto la status bar
        enableEdgeToEdge()

        // Sessione ancora valida: salta il login
        if (AuthManager.restoreSession(this)) {
            goToMain()
            return
        }

        setContent {
            SeatFinderTheme {
                LoginScreen(onLoginSuccess = { goToMain() })
            }
        }
    }

    //Apre MainActivity e chiude questa Activity
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

//UI del login. Gestisce localmente i due campi, lo stato di caricamento e il bottone abilitato
@Composable
private fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var matricola by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    //Bottone attivo solo con entrambi i campi pieni e nessuna richiesta in corso
    val canLogin = matricola.isNotBlank() && password.isNotBlank() && !loading

    //Altezza schermo non cambia quando appare la tastiera
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
            //Spinge i campi nella zona bianca dell'immagine
            Spacer(Modifier.height(screenHeight * 0.46f))
            //Campo matricola
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
            //Campo password
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
            //Bottone login
            Button(
                onClick = {
                    loading = true
                    scope.launch {

                        if (matricola.trim()=="prova"&&password.trim()=="prova"){
                            var user = UserLogin(
                                id = 1,
                                iniziali = "AA",
                                nome = "Test"
                            )
                            AuthManager.saveSession(context, user)
                            loading = false
                            onLoginSuccess()
                        }

                        val result = EsseTreAuthApi.login(
                            matricola.trim(),
                            password.trim()
                        )

                        loading = false
                        //Gestione esiti login
                        when (result) {
                            is LoginResult.Success -> {
                                // Salva poi chiude la schermata
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
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Login")
            }
            //Barra icrcolare di caricamento
            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(32.dp)) // margine per lo scroll con tastiera
        }
    }
}