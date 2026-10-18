package padelpulseapp2.netlify.app

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Tile del marcador: el partido de un vistazo desde el carrusel de tiles, sin
 * abrir la app, y un toque para volver al marcador.
 *
 * Solo enseña lo que el reloj sabe de verdad: sets, juegos y puntos del
 * partido guardado, y el pulso solo si el sensor lo esta leyendo ahora. Sin
 * partido, lo dice y ofrece empezar.
 *
 * El sistema no lo refresca solo: MainActivity pide una actualizacion cada vez
 * que cambia el marcador (requestUpdate). El sistema agrupa esas peticiones,
 * asi que puede ir unos segundos por detras de la app; para eso esta el toque.
 */
class ScoreTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val root = runCatching { layout(this) }
            .onFailure { Log.w(TAG, "Tile sin datos", it) }
            .getOrElse { fallback(this) }
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    companion object {
        private const val TAG = "PadelPulseTile"
        private const val RESOURCES_VERSION = "1"

        private const val WHITE = 0xFFF0F0F0.toInt()
        private const val DIM = 0xFF8A8A8A.toInt()
        private const val SURFACE = 0xFF1E1E1E.toInt()
        private const val ON_ACCENT_DARK = 0xFF000000.toInt()
        private const val HEART = 0xFFFF716C.toInt()

        /**
         * Pide al sistema que vuelva a pintar el tile. Se llama tras cada
         * cambio del marcador; si el tile no esta añadido, no hace nada.
         */
        fun refresh(context: Context) {
            runCatching { TileService.getUpdater(context).requestUpdate(ScoreTileService::class.java) }
        }

        /** El motor vivo si la app esta abierta; si no, el partido guardado. */
        private fun engine(context: Context): GameEngine =
            MainActivity.gameEngine ?: GameEngine(context.applicationContext)

        private fun accent(theme: String): Int =
            android.graphics.Color.parseColor(ThemeUtils.getHexColor(theme))

        private fun text(value: String, size: Float, color: Int, bold: Boolean = true): LayoutElementBuilders.Text =
            LayoutElementBuilders.Text.Builder()
                .setText(value)
                .setMaxLines(1)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(sp(size))
                        .setColor(argb(color))
                        .setWeight(
                            if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                            else LayoutElementBuilders.FONT_WEIGHT_NORMAL
                        )
                        .build()
                )
                .build()

        private fun spacer(height: Float): LayoutElementBuilders.Spacer =
            LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

        /** Abre el marcador. Lo llevan el boton y el tile entero. */
        private fun openApp(context: Context, id: String): ModifiersBuilders.Clickable =
            ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(context.packageName)
                                .setClassName(MainActivity::class.java.name)
                                .build()
                        )
                        .build()
                )
                .build()

        /** Pastilla con fondo y esquinas redondas (puntos del juego, boton). */
        private fun pill(
            content: LayoutElementBuilders.LayoutElement,
            background: Int,
            padH: Float,
            padV: Float,
            clickable: ModifiersBuilders.Clickable? = null
        ): LayoutElementBuilders.Box {
            val modifiers = ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(background))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(24f)).build())
                        .build()
                )
                .setPadding(
                    ModifiersBuilders.Padding.Builder()
                        .setStart(dp(padH)).setEnd(dp(padH))
                        .setTop(dp(padV)).setBottom(dp(padV))
                        .build()
                )
            if (clickable != null) modifiers.setClickable(clickable)
            return LayoutElementBuilders.Box.Builder()
                .setWidth(wrap())
                .setHeight(wrap())
                .setModifiers(modifiers.build())
                .addContent(content)
                .build()
        }

        private fun row(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.Row {
            val r = LayoutElementBuilders.Row.Builder()
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            items.forEach { r.addContent(it) }
            return r.build()
        }

        private fun hspace(width: Float): LayoutElementBuilders.Spacer =
            LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()

        /**
         * El tile: cabecera, set en curso, juegos en grande, puntos del juego,
         * pulso si lo hay y el boton de abrir. Todo centrado y en columna, que
         * en una esfera redonda es lo unico que nunca se corta.
         */
        fun layout(context: Context): LayoutElementBuilders.LayoutElement {
            val e = engine(context)
            val es = e.lang == "es"
            val acc = accent(e.theme)
            val activity = MainActivity.instance
            val live = activity?.timerRunning == true
            val enJuego = (e.hasSavedMatch() || live) && !e.over

            val col = LayoutElementBuilders.Column.Builder()
                .setWidth(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

            col.addContent(text(if (enJuego) (if (es) "● EN PISTA" else "● ON COURT") else "PADELPULSE LIVE", 11f, if (enJuego) acc else DIM))
            col.addContent(spacer(4f))

            if (enJuego) {
                val tb = e.isTb || e.isSuperTbActive()
                val fase = when {
                    e.isSuperTbActive() -> "SUPER TIE-BREAK"
                    e.isTb -> "TIE-BREAK"
                    else -> "SET ${e.setsA + e.setsB + 1} (${e.setsA}-${e.setsB})"
                }
                col.addContent(text(fase, 11f, WHITE))
                col.addContent(spacer(2f))

                // Juegos en grande; el de quien saca, en el color del tema
                val sacaA = e.serving == "A"
                col.addContent(
                    row(
                        text(e.gamesA.toString(), 40f, if (sacaA) acc else WHITE),
                        hspace(10f),
                        text("–", 28f, DIM),
                        hspace(10f),
                        text(e.gamesB.toString(), 40f, if (!sacaA) acc else WHITE)
                    )
                )
                // Nombres cortos debajo de cada cifra no caben en todas las
                // esferas: van en una sola linea, recortados
                col.addContent(text("${corto(e.nameA)} · ${corto(e.nameB)}", 10f, DIM, bold = false))
                col.addContent(spacer(4f))

                val puntos = if (tb) "${e.tbPtsA} · ${e.tbPtsB}"
                else "${e.getScoreStr("A")} · ${e.getScoreStr("B")}"
                col.addContent(pill(text(puntos, 14f, WHITE), SURFACE, 12f, 3f))

                // El pulso solo si el sensor lo esta dando ahora: con la app
                // cerrada no hay lectura y no se ensena una vieja como actual
                if (live && e.heartRate > 0) {
                    col.addContent(spacer(3f))
                    col.addContent(text("♥ ${e.heartRate} PPM", 11f, HEART))
                }
            } else {
                col.addContent(text(if (es) "Sin partido en curso" else "No match in progress", 13f, WHITE, bold = false))
            }

            col.addContent(spacer(8f))
            col.addContent(
                pill(
                    text(
                        if (enJuego) (if (es) "ABRIR MARCADOR" else "OPEN SCOREBOARD")
                        else (if (es) "EMPEZAR" else "START"),
                        12f, ON_ACCENT_DARK
                    ),
                    acc, 16f, 9f, openApp(context, "open_button")
                )
            )

            // El tile entero tambien abre el marcador
            return LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(openApp(context, "open_tile"))
                        .build()
                )
                .addContent(col.build())
                .build()
        }

        /** Si algo falla leyendo el partido, al menos el boton de abrir. */
        private fun fallback(context: Context): LayoutElementBuilders.LayoutElement =
            LayoutElementBuilders.Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(pill(text("PADELPULSE", 12f, ON_ACCENT_DARK), 0xFF00FD87.toInt(), 16f, 9f, openApp(context, "open_fallback")))
                .build()

        /** "YO Y PAREJA" -> "YO Y PAR…": la linea de nombres tiene poco sitio. */
        private fun corto(nombre: String): String {
            val n = nombre.trim().uppercase()
            return if (n.length <= 9) n else n.take(8) + "…"
        }
    }
}
