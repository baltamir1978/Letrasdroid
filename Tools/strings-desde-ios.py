#!/usr/bin/env python3
"""res/values*/strings.xml desde el catálogo de iOS (Tools/Localizable-ios.xcstrings, copiado de
Letrasios): cada nombre de Android se asocia a la frase en español de iOS. Las frases propias de
Android (YouTube en vez de Apple Music) van en EXTRA con sus traducciones.
Uso: python3 Tools/strings-desde-ios.py"""
import json, os
here = os.path.dirname(__file__)
cat = json.load(open(os.path.join(here, "Localizable-ios.xcstrings")))["strings"]
res = os.path.join(here, "..", "app", "src", "main", "res")
LANGS = ["es", "en", "fr", "de", "it", "pt"]
FROM_IOS = {
    "about_text": "Abre la app cerca de la música: Letras reconoce la canción que suena y muestra su letra sincronizada con ella.",
    "open_settings": "Abrir Ajustes", "about": "Acerca de Letras", "delete": "Borrar", "clear_history": "Borrar el historial",
    "searching_lyrics": "Buscando la letra…", "credits": "Créditos", "stop_listening": "Dejar de escuchar",
    "privacy_text": "El audio no se graba ni se guarda: solo se envía a Shazam una huella digital para identificar la canción. La app no recoge datos ni usa analítica.",
    "paused": "En pausa", "listening": "Escuchando…", "listen": "Escuchar", "no_lyrics_song": "Esta canción no tiene letra.",
    "history": "Historial", "instrumental": "Instrumental", "history_empty_hint": "Las canciones que reconozca Letras aparecerán aquí.",
    "not_synced": "Letra sin sincronizar", "lyrics_credit": "Letras",
    "mic_needed": "Letras necesita el micrófono para reconocer la música que suena. Puedes activarlo en Ajustes.",
    "done": "Listo", "mark_favorite": "Marcar como favorita", "not_found": "No hemos encontrado la letra de esta canción.",
    "cant_recognize": "No reconozco lo que suena. Acerca el móvil a la música.", "download_failed": "No se ha podido descargar la letra.",
    "instrumental_break": "Pausa instrumental", "play_music_hint": "Pon música cerca del móvil y aparecerá la letra.",
    "privacy": "Privacidad", "tap_mic_hint": "Pulsa el micrófono para volver a escuchar.", "unmark_favorite": "Quitar de favoritas",
    "music_recognition": "Reconocimiento musical", "retry": "Reintentar", "no_mic": "Sin acceso al micrófono",
    "no_songs": "Sin canciones", "no_connection": "Sin conexión", "no_lyrics": "Sin letra", "version": "Versión",
    "clear_confirm": "¿Borrar el historial? Se conservan las canciones marcadas con el corazón.",
}
EXTRA = {
    "search_youtube": ["Buscar en YouTube", "Search YouTube", "Rechercher sur YouTube", "Auf YouTube suchen", "Cerca su YouTube", "Procurar no YouTube"],
    "open_youtube": ["Abrir en YouTube", "Open in YouTube", "Ouvrir dans YouTube", "In YouTube öffnen", "Apri in YouTube", "Abrir no YouTube"],
    "back": ["Volver", "Back", "Retour", "Zurück", "Indietro", "Voltar"],
    "cancel": ["Cancelar", "Cancel", "Annuler", "Abbrechen", "Annulla", "Cancelar"],
}
def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'").replace('"', '\\"')
for i, lang in enumerate(LANGS):
    d = os.path.join(res, "values" if lang == "es" else f"values-{lang}")
    os.makedirs(d, exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    if lang == "es": out.append('    <string name="app_name">Letras</string>')
    for name, key in FROM_IOS.items():
        v = key if lang == "es" else cat[key]["localizations"][lang]["stringUnit"]["value"]
        v = v.replace("des iPhone", "des Handys").replace("das iPhone", "das Handy")   # en Android no es un iPhone
        out.append(f'    <string name="{name}">{esc(v)}</string>')
    for name, vals in EXTRA.items():
        out.append(f'    <string name="{name}">{esc(vals[i])}</string>')
    out.append("</resources>")
    open(os.path.join(d, "strings.xml"), "w").write("\n".join(out) + "\n")
print("strings.xml en", ", ".join(LANGS))
