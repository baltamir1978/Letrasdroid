package es.altamirano.letras.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Modo escritorio (ventanas que se redimensionan), tableta o móvil tumbado.

/** Ancho al que el contenido deja de estirarse. */
val READABLE_WIDTH = 720.dp

/**
 * El margen lateral para que el contenido no pase de [max] y quede centrado, nunca menos de
 * [min]. Va como `contentPadding` de la lista, no limitando la lista: así la rueda del ratón
 * desplaza también sobre los márgenes.
 */
@Composable
fun gutter(min: Dp = 16.dp, max: Dp = READABLE_WIDTH): Dp {
    val w = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(min, (w - max) / 2)
}

/** El clic derecho del ratón (modo escritorio), para lo que en el móvil es deslizar o pulsación larga. */
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = composed {
    val current = rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                    e.changes.forEach { it.consume() }
                    current.value()
                }
            }
        }
    }
}

/** Centrado en la ventana si cabe; si es baja (modo escritorio, móvil tumbado), se desplaza. */
@Composable
fun CenteredScroll(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(padding),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
