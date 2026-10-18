package com.basic.spendai.ai

import com.basic.spendai.ui.components.gemmaProgressText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProvidersTest {

    @Test
    fun nanoThenGemmaThenRules() {
        assertEquals(AiTier.NANO, AiProviders.choose(enabled = true, nanoAvailable = true, gemmaInstalled = true))
        assertEquals(AiTier.GEMMA, AiProviders.choose(enabled = true, nanoAvailable = false, gemmaInstalled = true))
        assertEquals(AiTier.RULES, AiProviders.choose(enabled = true, nanoAvailable = false, gemmaInstalled = false))
    }

    @Test
    fun switchedOffMeansRulesEvenWithModels() {
        assertEquals(AiTier.RULES, AiProviders.choose(enabled = false, nanoAvailable = true, gemmaInstalled = true))
    }

    @Test
    fun spaceCheckLeavesRoomToSpare() {
        val model = GemmaModel.EXPECTED_BYTES
        assertFalse(GemmaModel.hasRoomFor(model, free = model)) // exactly the model's size isn't enough
        assertFalse(GemmaModel.hasRoomFor(model, free = model + 299_000_000))
        assertTrue(GemmaModel.hasRoomFor(model, free = model + 300_000_000))
        assertEquals("Not enough free space: the model needs about 884 MB free.", GemmaModel.notEnoughSpace(model))
    }
}

class GemmaDownloadTest {
    @Test
    fun sha256OfAFile() {
        val file = java.io.File.createTempFile("gemma", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GemmaModel.sha256(file))
    }

    @Test
    fun progressWording() {
        val text = ::gemmaProgressText
        assertEquals("Downloading… 120 MB of 584 MB", text(GemmaState.Copying(120_400_000, 584_417_280, downloading = true)))
        assertEquals("Copying… 8 MB", text(GemmaState.Copying(8_000_000, null, downloading = false)))
        assertEquals(
            "Waiting for Wi-Fi… The download starts when you're connected.",
            text(GemmaState.Copying(0, null, downloading = true, waitingForWifi = true)),
        )
        assertEquals("Checking the file…", text(GemmaState.Copying(584_417_280, 584_417_280, downloading = true, verifying = true)))
    }
}
