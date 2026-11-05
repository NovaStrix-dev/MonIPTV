package com.perso.moniptv

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Réglage de l'apparence des sous-titres (issue #5). Chaque changement est appliqué tout de suite
 * à la vidéo (visible derrière la fenêtre) et à l'aperçu, et mémorisé pour les prochaines lectures.
 */
@Composable
fun SubtitleStyleDialog(
    style: SubtitleStyle,
    onChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Style des sous-titres") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Preview(style)
                Spacer(Modifier.height(12.dp))
                Choice("Taille", SubSize.entries, style.size, { it.label }) { onChange(style.copy(size = it)) }
                Choice("Couleur", SubColor.entries, style.color, { it.label }) { onChange(style.copy(color = it)) }
                Choice("Lisibilité", SubBackground.entries, style.background, { it.label }) {
                    onChange(style.copy(background = it))
                }
                Choice("Position", SubPosition.entries, style.position, { it.label }) { onChange(style.copy(position = it)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Texte en gras", Modifier.weight(1f))
                    Switch(checked = style.bold, onCheckedChange = { onChange(style.copy(bold = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onChange(SubtitleStyle()) }) { Text("Par défaut") } },
    )
}

@Composable
private fun <T> Choice(label: String, values: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onPick(v) },
                label = { Text(name(v)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF26365A),
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

/** Aperçu approximatif du rendu, utile quand aucun sous-titre n'est affiché à ce moment de la vidéo. */
@Composable
private fun Preview(s: SubtitleStyle) {
    val shadow = when (s.background) {
        SubBackground.OUTLINE -> Shadow(Color.Black, Offset.Zero, blurRadius = 6f)
        SubBackground.SHADOW -> Shadow(Color.Black, Offset(3f, 3f), blurRadius = 4f)
        else -> null
    }
    val bg = when (s.background) {
        SubBackground.BOX -> Color(0xA0000000)
        SubBackground.SOLID -> Color.Black
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5B7DB1), Color(0xFFB9A07A)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Exemple de sous-titre",
            style = TextStyle(
                color = Color(s.color.argb),
                fontSize = (15 * s.size.scale).sp,
                fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
                shadow = shadow,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .padding(bottom = if (s.position == SubPosition.HIGHER) 28.dp else 8.dp)
                .background(bg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
