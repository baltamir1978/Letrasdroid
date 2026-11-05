package es.altamirano.letras.core

import es.altamirano.letras.data.HeardSong
import es.altamirano.letras.data.History
import es.altamirano.letras.net.Shazam
import es.altamirano.letras.net.ShazamOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test fun lrc() {
        val lines = Lrc.parse("[ar:Queen]\n[offset:+500]\n[00:12.00][01:40.50] Estribillo\n[00:05,25]Primera\n[00:20.00]\n")
        assertEquals(listOf("Primera", "Estribillo", "", "Estribillo"), lines.map { it.text })
        assertEquals(4.75, lines[0].time, 1e-9)   // +500 ms adelanta la letra
        assertEquals(null, lines.currentIndex(4.0))
        assertEquals(1, lines.currentIndex(12.0))
        assertEquals(3, lines.currentIndex(500.0))
    }

    @Test fun cleanAndArtist() {
        assertEquals("Song", Lrc.cleanTitle("Song (feat. X) - Remastered 2011"))
        assertEquals("Artist A", Lrc.primaryArtist("Artist A & Artist B"))
        assertEquals("Rosalía", Lrc.primaryArtist("Rosalía feat. J Balvin"))
        val s = Song("Song (feat. X)", "A & B")
        assertEquals(listOf("Song (feat. X)" to "A & B", "Song" to "A & B", "Song" to "A"), Lrc.attempts(s))
        assertTrue(s.youtubeUrl.startsWith("https://www.youtube.com/results?search_query=A+%26+B+Song"))
    }

    @Test fun pickPrefersSynced() {
        val arr = Json.parseToJsonElement("""[
            {"trackName":"Hello","plainLyrics":"hola","duration":200},
            {"trackName":"Hello (Live)","syncedLyrics":"[00:01.00]hi","duration":210},
            {"trackName":"Instr","instrumental":true}]""") as JsonArray
        val r = Lrc.pick(arr, "Hello")!!
        assertTrue(r.lyrics is Lyrics.Synced); assertEquals(210.0, r.duration!!, 1e-9)
        assertEquals(Lyrics.Instrumental, Lrc.pick(Json.parseToJsonElement("""[{"instrumental":true}]""") as JsonArray, "x")!!.lyrics)
        assertNull(Lrc.pick(JsonArray(emptyList()), "x"))
    }

    @Test fun shazamParse() {
        val m = Shazam.parse("""{"matches":[{"id":"1","offset":83.5}],"track":{"key":"42","title":"Bohemian Rhapsody","subtitle":"Queen",
            "images":{"coverart":"https://x/c.jpg"},"url":"https://www.shazam.com/track/42"}}""") as ShazamOutcome.Match
        assertEquals("Queen", m.song.artist); assertEquals(83.5, m.offset, 1e-9); assertEquals("42", m.song.shazamId)
        assertEquals(ShazamOutcome.NoMatch, Shazam.parse("""{"matches":[]}"""))
        assertEquals(ShazamOutcome.Failed, Shazam.parse("no es json"))
    }

    @Test fun historyCapKeepsFavorites() {
        val fav = HeardSong(song = Song("Fav", "A"), heardAt = 0, favorite = true)
        val list = List(History.MAX) { HeardSong(song = Song("S$it", "A"), heardAt = it.toLong()) } + fav
        val trimmed = History.trim(list)
        assertEquals(History.MAX, trimmed.size)
        assertTrue(trimmed.contains(fav))
    }

    @Test fun sameSong() {
        assertTrue(Song("a", "b", shazamId = "1").isSameSong(Song("x", "y", shazamId = "1")))
        assertTrue(Song("Hey", "Beatles").isSameSong(Song("hey", "beatles")))
    }
}
