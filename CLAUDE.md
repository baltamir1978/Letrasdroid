# Letrasdroid — contexto del repositorio

Versión para Android de **Letras** (Letrasios, iOS), escrita **desde cero en Kotlin + Jetpack
Compose**. Lo común a las apps de Android (firma, `versionCode`, secretos, distribución, ShazamKit)
está en el `CLAUDE.md` de la carpeta de proyectos; aquí va lo propio de esta.

## Estado (5-nov-2026)

**0.1: compila y pasan 9 pruebas; sin probar en el móvil** (el micrófono no se puede probar
aquí). APK en `~/Documentos/Letras-0.1.apk`. En el móvil se llama **Letras**, como en el iPhone;
paquete `es.altamirano.letras`, clave en `~/.android/claves/letras-release.*`.

**Sin ShazamKit** (Bruno da de baja su cuenta de Apple, 5-nov-2026): el plan de octubre (SDK de
ShazamKit para Android con su *developer token*) ya no vale. Se reconoce como en RadioDroid: la
huella se calcula en el móvil (`core/ShazamSignature.kt`, copiada tal cual de RadioDroid, que la
comprobó byte a byte contra shazamio) y se manda a `amp.shazam.com` como hace la app de Shazam
(`net/Net.kt`). **No es una API pública**: si Shazam la cambia, deja de reconocer.

Hecho, traducido de Letrasios:
- `data/Listener.kt` (`SongListener`): `AudioRecord` a 16 kHz mono en un búfer circular de 12 s;
  primer intento con 5 s de audio, luego cada 3 s nuevos con los últimos 10 s; tras reconocer la
  que suena, 8 s antes de volver a preguntar; cuatro fallos seguidos o pasar de la duración de
  LRCLIB quitan la canción. Shazam da el segundo de la canción en que **empieza** el trozo, y se
  sabe cuándo se grabó ese principio: posición = ese segundo + lo transcurrido. Sólo con la app a
  la vista (`onStart`/`onStop`, sin servicio), y la pantalla no se apaga mientras escucha.
- Letra de LRCLIB (`core/Lyrics.kt`, `LyricsService`): tal cual, título limpio y artista
  principal; prefiere la sincronizada y el título exacto; `[offset:]` respetado.
- Pantallas (`ui/Main.kt`): escuchando / en pausa / «no reconozco», permiso denegado (con botón a
  Ajustes), canción con carátula (Coil) y enlace a **YouTube** (búsqueda «artista título», como se
  decidió), letra sincronizada (0,3 s de adelanto, la línea a un tercio, deja de seguir 4 s al
  arrastrar, TalkBack con la línea actual seleccionada y «Pausa instrumental»), sin sincronizar,
  instrumental, sin letra y sin conexión. Historial por días (corazón, deslizar para borrar o
  marcar, «Borrar» conserva favoritas, 500 como mucho, la misma canción en 15 min no se repite),
  letra de una canción pasada como texto, y «Acerca de».
- Colores de `Theme.swift` (verde #0B6F53 / menta #4FD8A6), icono de iOS
  (`Tools/icono-desde-ios.sh`), 6 idiomas desde el catálogo de iOS (`Tools/strings-desde-ios.py`).

Falta: probarlo en el móvil (sobre todo cuánto tarda en reconocer con el micrófono y si la
sincronización cuadra; el audio de `AudioRecord` llega con algo de retraso, sin corregir),
widgets o accesos directos si Bruno los quiere.

## La especificación es el original

`../Letrasios` (`github.com/baltamir1978/Letrasios`): leer su `README.md`. Qué hace:

- **Empieza a escuchar al abrir**, sin pulsar nada; deja de escuchar en segundo plano y vuelve al
  volver.
- Reconoce con Shazam y muestra la **letra sincronizada de LRCLIB**: la línea actual resaltada y la
  vista siguiéndola. Si LRCLIB sólo tiene la letra sin tiempos, se muestra para leerla; si la
  canción es instrumental, se dice.
- **Sigue escuchando y corrige la posición** con cada reconocimiento; si cambia la canción, cambia
  la letra; si para la música, vuelve a la pantalla de escucha.
- **Al arrastrar la letra, deja de seguir la canción unos segundos.**
- **Historial** por días, con carátula y hora: tocar abre la letra; el corazón marca favoritas, que
  sobreviven a «Borrar» y al tope. Máximo 500; la misma canción en menos de 15 min no se repite.
- Enlace a Apple Music (o a una búsqueda si no hay letra).
- Modo claro y oscuro, accesibilidad completa, 6 idiomas (es, en, fr, de, it, pt), sin cuentas ni
  analítica.

## Cómo se hace en Android

| En iOS | Aquí |
|---|---|
| `SHManagedSession` (gestiona el micrófono sola) | `AudioRecord` (PCM 16 bits, 16 kHz) + la huella y la petición de Shazam de RadioDroid, sin ShazamKit |
| Permiso de micrófono | `RECORD_AUDIO` en tiempo de ejecución. Sólo se escucha en primer plano, así que no hace falta servicio en primer plano: parar en `onStop` |
| `history.json` en Documentos | `historial.json` en `filesDir` |
| VoiceOver, Dynamic Type | TalkBack (`semantics`, la línea actual como seleccionada) y tamaño de letra del sistema en `sp` |
| String Catalogs | `strings.xml` por idioma |

## Decidido con Bruno (21-oct-2026)

- **El enlace a Apple Music pasa a ser a YouTube**: una búsqueda «artista título»
  (`https://www.youtube.com/results?search_query=…`), que abre la app de YouTube si está instalada
  y el navegador si no (en GrapheneOS puede no haber app de YouTube).
- **Tiene que funcionar en GrapheneOS**, sin Google Play: no usa nada de Google.

## Trampas que vienen de las apps hermanas

- **LRCLIB no encuentra las colaboraciones**: buscar también por el artista principal, y por título
  filtrando por artista (aprendido en MacRadio).
- La etiqueta LRC `[offset:]` mueve toda la letra; respetarla.
- Shazam: el `offset` es el segundo de la canción en que **empieza** el trozo enviado (medido en
  RadioDroid), no el del final.
