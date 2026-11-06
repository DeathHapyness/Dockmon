package dev.rique.guardiao.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun default_is_branco_with_original_colors() {
        val t = Themes.byId("branco")
        assertEquals(0xFFF2FBFF.toInt(), t.core)
        assertEquals(0xFFBFEFFF.toInt(), t.glow)
        assertEquals(Themes.all[0], t)
    }

    @Test
    fun unknown_id_falls_back_to_branco() {
        assertEquals("branco", Themes.byId("nope").id)
        assertEquals("branco", Themes.byId(null).id)
    }

    @Test
    fun ids_unique_and_several() {
        assertTrue(Themes.all.size >= 4)
        assertEquals(Themes.all.size, Themes.all.map { it.id }.toSet().size)
        assertEquals("ambar", Themes.byId("ambar").id)
    }
}
