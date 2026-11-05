@file:OptIn(UnstableApi::class)

package com.perso.moniptv

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** Taille du texte, par rapport à la taille par défaut du lecteur. */
enum class SubSize(val label: String, val scale: Float) {
    SMALL("Petite", 0.8f),
    NORMAL("Normale", 1f),
    LARGE("Grande", 1.3f),
    XLARGE("Très grande", 1.65f),
}

enum class SubColor(val label: String, val argb: Int) {
    WHITE("Blanc", 0xFFFFFFFF.toInt()),
    YELLOW("Jaune", 0xFFFFE14D.toInt()),
    GREEN("Vert", 0xFF8BF5A0.toInt()),
    CYAN("Cyan", 0xFF7FE3F2.toInt()),
}

/** Ce qui rend le texte lisible sur l'image : contour, ombre ou bandeau de fond. */
enum class SubBackground(val label: String) {
    OUTLINE("Contour"),
    SHADOW("Ombre"),
    BOX("Fond transparent"),
    SOLID("Fond noir"),
}

/** Hauteur des sous-titres (part de la hauteur de l'image laissée sous le texte). */
enum class SubPosition(val label: String, val bottom: Float) {
    BOTTOM("En bas", 0.08f),
    HIGHER("Plus haut", 0.2f),
}

/** Apparence des sous-titres choisie par l'utilisateur (issue #5), mémorisée pour toutes les vidéos. */
data class SubtitleStyle(
    val size: SubSize = SubSize.NORMAL,
    val color: SubColor = SubColor.WHITE,
    val background: SubBackground = SubBackground.OUTLINE,
    val bold: Boolean = false,
    val position: SubPosition = SubPosition.BOTTOM,
) {
    /**
     * Applique le style au lecteur. Les styles intégrés au fichier (couleurs, tailles des sous-titres
     * SSA/ASS ou TTML) sont ignorés, sinon ils prendraient le dessus sur le choix de l'utilisateur.
     * 0,0533 et 0,08 : taille du texte et marge du bas par défaut du lecteur Media3.
     */
    fun applyTo(view: SubtitleView) {
        val bg = when (background) {
            SubBackground.BOX -> 0xA0000000.toInt()
            SubBackground.SOLID -> 0xFF000000.toInt()
            else -> 0
        }
        val edge = when (background) {
            SubBackground.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubBackground.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(
            CaptionStyleCompat(
                color.argb, bg, 0, edge, 0xFF000000.toInt(),
                if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
            )
        )
        view.setFractionalTextSize(0.0533f * size.scale)
        view.setBottomPaddingFraction(position.bottom)
    }

    companion object {
        private const val PREFS = "player"

        fun load(ctx: Context): SubtitleStyle {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = SubtitleStyle()
            return SubtitleStyle(
                size = enumOr(p.getString("sub_size", null), d.size),
                color = enumOr(p.getString("sub_color", null), d.color),
                background = enumOr(p.getString("sub_background", null), d.background),
                bold = p.getBoolean("sub_bold", d.bold),
                position = enumOr(p.getString("sub_position", null), d.position),
            )
        }

        fun save(ctx: Context, s: SubtitleStyle) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sub_size", s.size.name)
                .putString("sub_color", s.color.name)
                .putString("sub_background", s.background.name)
                .putBoolean("sub_bold", s.bold)
                .putString("sub_position", s.position.name)
                .apply()
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}
