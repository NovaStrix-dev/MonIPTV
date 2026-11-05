package com.perso.moniptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: AppViewModel) {
    var type by rememberSaveable { mutableStateOf(AccountType.XTREAM) }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    val xtream = type == AccountType.XTREAM

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text("MonIPTV", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Connectez votre abonnement une seule fois.", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = xtream, onClick = { type = AccountType.XTREAM }, label = { Text("Xtream Codes") })
                FilterChip(selected = !xtream, onClick = { type = AccountType.M3U }, label = { Text("Lien M3U") })
            }

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(if (xtream) "Adresse du serveur" else "URL M3U ou adresse du serveur") },
                placeholder = { Text(if (xtream) "http://exemple.com:8080" else "http://exemple.com/get.php?...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(if (xtream) "Identifiant" else "Identifiant (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(if (xtream) "Mot de passe" else "Mot de passe (optionnel)") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Afficher")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!xtream) {
                Text(
                    "Collez le lien complet de votre playlist, ou bien l'adresse du serveur avec votre identifiant et mot de passe.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                VpnBypassButton(vm, onEnabled = { vm.login(Account(type, server, user, pass)) })
            }

            val canSubmit = !vm.loading && server.isNotBlank() &&
                (!xtream || (user.isNotBlank() && pass.isNotBlank()))
            Button(
                onClick = { vm.login(Account(type, server, user, pass)) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("Chargement du catalogue…")
                } else {
                    Text("Se connecter")
                }
            }
            Text(
                "Vos identifiants sont chiffrés et conservés uniquement sur cet appareil. Ils ne vous seront plus demandés.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
