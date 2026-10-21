# Letrasdroid — contexto del repositorio

Versión para Android de **Letras** (Letrasios, iOS), escrita **desde cero en Kotlin + Jetpack
Compose**. Lo común a las apps de Android (firma, `versionCode`, secretos, distribución, ShazamKit)
está en el `CLAUDE.md` de la carpeta de proyectos; aquí va lo propio de esta.

## Estado (21-oct-2026)

**Sin empezar.** Es la **piloto de Android**: es la más pequeña (unas 1.400 líneas en iOS) y con
ella se comprueba lo más arriesgado de todo el lado Android, antes de RadioDroid, que lo reutiliza:

1. que el SDK de ShazamKit para Android y su *developer token* funcionan;
2. la cadena entera: SDK instalado en el Mac, `./gradlew`, firma y distribución.

En el Mac todavía no hay nada de Android instalado, y Bruno aún no tiene móvil: hasta entonces,
emulador, que **no oye música de verdad**.

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
| `SHManagedSession` (gestiona el micrófono sola) | SDK de ShazamKit para Android: sesión de *streaming* alimentada con `AudioRecord` (PCM 16 bits) |
| Permiso de micrófono | `RECORD_AUDIO` en tiempo de ejecución. Sólo se escucha en primer plano, así que no hace falta servicio en primer plano: parar en `onStop` |
| `history.json` en Documentos | Room o un JSON en `filesDir` (con 500 entradas basta el JSON) |
| VoiceOver, Dynamic Type | TalkBack (`semantics`, la línea actual como seleccionada) y tamaño de letra del sistema en `sp` |
| String Catalogs | `strings.xml` por idioma |

## Decidido con Bruno (21-oct-2026)

- **El enlace a Apple Music pasa a ser a YouTube**: una búsqueda «artista título»
  (`https://www.youtube.com/results?search_query=…`), que abre la app de YouTube si está instalada
  y el navegador si no (en GrapheneOS puede no haber app de YouTube).
- **Tiene que funcionar en GrapheneOS**, sin Google Play. El SDK de ShazamKit sólo depende de
  coroutines, OkHttp y Retrofit (ver el `CLAUDE.md` común): falta confirmarlo en el manifiesto del
  AAR al descargarlo.

## Lo que hace falta del portal de Apple (lo hace Bruno, con su cuenta)

1. **Identifiers › + › Media IDs**: uno nuevo con **ShazamKit** marcado.
2. **Keys › +**: una clave con **Media Services (MusicKit, ShazamKit)**, asociada a ese Media ID.
   El `.p8` sólo se descarga una vez: a `~/.config/shazamkit/`.
3. `~/.config/shazamkit/token.env` (permisos 600) con `SHAZAM_KEY_ID`, `SHAZAM_TEAM_ID` y
   `SHAZAM_KEY_FILE`.
4. **El AAR**: developer.apple.com/shazamkit/android, a `libs/` (ignorado por git).

Con eso, `Tools/shazam-token.py` da el token, que entra en `local.properties` al compilar.
`Tools/shazam-token.py --comprobar <token>` dice cuándo caduca.

## Trampas que vienen de las apps hermanas

- **LRCLIB no encuentra las colaboraciones**: buscar también por el artista principal, y por título
  filtrando por artista (aprendido en MacRadio).
- La etiqueta LRC `[offset:]` mueve toda la letra; respetarla.
- El token de ShazamKit **nunca** en el repositorio, que será público como el original.
