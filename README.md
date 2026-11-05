# Letrasdroid

En el móvil se llama **Letras**. Escucha la música que suena alrededor, reconoce la canción con
Shazam y muestra su letra sincronizada, resaltando la línea que se está cantando.

Versión para Android de [Letras (Letrasios)](https://github.com/baltamir1978/Letrasios), la app de
iOS, escrita desde cero en Kotlin + Jetpack Compose, con la app original como especificación.

**Estado: 0.1** (5-nov-2026), sin probar todavía en el móvil. Ver `CLAUDE.md`.

## Qué hace

- **Empieza a escuchar al abrir**, sin pulsar nada; deja de escuchar al salir y vuelve al volver.
- **Reconoce con Shazam sin ShazamKit**: la huella del audio se calcula en el móvil y se manda a
  Shazam como hace su app. No se manda audio. No es una API pública: si Shazam la cambia, deja de
  reconocer.
- **Letra sincronizada de [LRCLIB](https://lrclib.net)**: la línea actual resaltada y la vista
  siguiéndola. Si sólo hay letra sin tiempos, se muestra para leerla; si es instrumental, se dice.
- **Sigue escuchando y corrige la posición** con cada reconocimiento; si cambia la canción,
  cambia la letra; si para la música, vuelve a la pantalla de escucha.
- Al arrastrar la letra, deja de seguir la canción unos segundos.
- **Historial** por días, con carátula y hora; el corazón marca favoritas, que sobreviven a
  «Borrar».
- Enlace a una búsqueda en **YouTube** de la canción.
- Modo claro y oscuro, TalkBack, 6 idiomas (es, en, fr, de, it, pt). Sin cuentas ni analítica.

## Pila

Kotlin + Jetpack Compose, para **Android 12 o posterior**, también en **GrapheneOS** (sin los
servicios de Google).

## Compilar

```sh
Tools/instalar-entorno.sh      # JDK 17, Android SDK y Gradle en ~/Android, sin sudo
./gradlew testDebugUnitTest assembleRelease
```

La release se firma con una clave que vive fuera del repositorio (`~/.android/claves/`); sin ella
sale sin firmar.

## Créditos

Huella de Shazam traducida de [shazamio](https://github.com/shazamio/ShazamIO) (MIT) y
[SongRec](https://github.com/marin-m/SongRec). Letras: LRCLIB. Las letras tienen derechos de autor:
uso personal.
