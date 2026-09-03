package bpm.munkz.pulse_wear.os.bpm.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FidgetLocalizationTest {
    @Test
    fun everyToyAndCategoryHasARealSpanishLabel() {
        assertEquals(26, FIDGET_TOY_INFOS.size)

        FIDGET_TOY_INFOS.forEach { toy ->
            val englishName = toy.nameFor(AppLanguage.English)
            val spanishName = toy.nameFor(AppLanguage.Spanish)
            val englishStyle = toy.styleFor(AppLanguage.English)
            val spanishStyle = toy.styleFor(AppLanguage.Spanish)

            assertTrue(spanishName.isNotBlank())
            assertTrue(spanishStyle.isNotBlank())
            assertNotEquals(englishName, spanishName)
            assertNotEquals(englishStyle, spanishStyle)
        }
    }

    @Test
    fun spanishNavigationWallAndHomeToyControlsAreLocalized() {
        val spanish = fidgetTextFor(AppLanguage.Spanish)

        assertEquals("Juguetes", spanish.toys)
        assertEquals("Muro de Juguetes", spanish.toyWall)
        assertEquals("Juguetes Fijados", spanish.pinnedFidgets)
        assertEquals("Fijar", spanish.pin)
        assertEquals("Fijado", spanish.pinned)
        assertEquals("DÍA", spanish.day)
        assertEquals("NOCHE", spanish.night)
        assertEquals("ABIERTA", spanish.open)
        assertEquals("CERRADA", spanish.closed)
        assertEquals("Estilo del spinner", spanish.spinnerStyle)
        assertEquals("Distribución del spinner", spanish.spinnerLayout)
        assertEquals("Uno", spanish.singleSpinner)
        assertEquals("Múltiple", spanish.multiSpinner)
        assertEquals("Gestos", spanish.gestures)
        assertEquals("Inclinación", spanish.tilt)
        assertEquals("Agitar", spanish.shake)
        assertEquals(listOf("Bombo", "Caja", "Hat", "Tom", "Palma", "Camp."), spanish.beatPadLabels)
        assertEquals("C", spanish.hotInitial)
        assertEquals("F", spanish.coldInitial)
    }

    @Test
    fun toyTitleUsesTheSelectedLanguage() {
        val spanish = fidgetTextFor(AppLanguage.Spanish)

        assertEquals(
            "Máquina de Ritmos",
            fidgetToyNameFor(FIDGET_BEAT_MACHINE_INDEX, AppLanguage.Spanish, spanish),
        )
        assertEquals(
            spanish.toyWall,
            fidgetToyNameFor(FIDGET_WALL_INDEX, AppLanguage.Spanish, spanish),
        )
    }
}
