package padelpulseapp2.netlify.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Margenes para que el bisel de un reloj redondo no se coma nada.
 *
 * En redondo lo visible es el circulo inscrito en la pantalla cuadrada, asi que
 * el ancho util no es constante: se estrecha muy deprisa al acercarse al borde
 * de arriba o al de abajo. Una fila al 96% de ancho cabe de sobra en el centro
 * y queda cortada por las puntas si esta pegada al primer o al ultimo hueco.
 *
 * Los porcentajes salen de la geometria del circulo, no a ojo: con un 10% de
 * margen lateral el contenido ocupa el 80% del ancho, y para que esa anchura
 * quepa entera hay que bajarla un 18% del ancho desde el borde superior.
 */
object RoundSafe {
    const val HORIZONTAL = 0.10f
    const val VERTICAL = 0.18f

    /** Lo mas ancho que puede ser una fila sin tocar el bisel. */
    const val EDGE_WIDTH = 0.92f
    const val CENTER_WIDTH = 1.0f
}

@Composable
@ReadOnlyComposable
fun isRoundScreen(): Boolean = LocalConfiguration.current.isScreenRound

/**
 * Relleno del ScalingLazyColumn. En cuadrado se queda como estaba; en redondo
 * aparta el contenido del arco.
 */
@Composable
@ReadOnlyComposable
fun roundSafePadding(
    squareHorizontal: Dp = 8.dp,
    squareVertical: Dp = 26.dp,
    extraVertical: Dp = 0.dp
): PaddingValues {
    val cfg = LocalConfiguration.current
    if (!cfg.isScreenRound) {
        return PaddingValues(
            horizontal = squareHorizontal,
            vertical = squareVertical + extraVertical
        )
    }
    val w = cfg.screenWidthDp.dp
    return PaddingValues(
        horizontal = w * RoundSafe.HORIZONTAL,
        vertical = w * RoundSafe.VERTICAL + extraVertical
    )
}

/**
 * Relleno para pantallas que no scrollean (splash, cuenta, fin de partido).
 * Ahi el contenido va centrado, asi que el limite lo pone la altura del bloque,
 * y conviene dejar el mismo hueco arriba y abajo que a los lados.
 */
@Composable
@ReadOnlyComposable
fun roundSafeBoxPadding(square: Dp = 16.dp): PaddingValues {
    val cfg = LocalConfiguration.current
    if (!cfg.isScreenRound) return PaddingValues(horizontal = square, vertical = square)
    val w = cfg.screenWidthDp.dp
    return PaddingValues(
        horizontal = w * RoundSafe.HORIZONTAL,
        vertical = w * (RoundSafe.VERTICAL * 0.6f)
    )
}

/**
 * Fraccion de ancho para una fila. [edge] marca las que van en el primer o el
 * ultimo hueco de la lista, que son las que el arco recorta.
 */
@Composable
@ReadOnlyComposable
fun safeWidth(edge: Boolean = false, square: Float = 0.96f): Float {
    if (!isRoundScreen()) return square
    return if (edge) RoundSafe.EDGE_WIDTH else RoundSafe.CENTER_WIDTH
}
