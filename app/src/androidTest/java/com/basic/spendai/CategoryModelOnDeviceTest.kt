package com.basic.spendai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.Category
import com.basic.spendai.sms.CategoryGuesser
import com.basic.spendai.sms.CategoryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled category model loads from the APK and answers quickly on a real phone (logged as "CategoryModel"). */
@RunWith(AndroidJUnit4::class)
class CategoryModelOnDeviceTest {
    @Test
    fun loadsFromTheApkAndAnswersFast() {
        val loadStart = System.nanoTime()
        val model = CategoryModel.bundled
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000
        assertNotNull("category_model.bin should be packaged in the APK", model)

        val texts = listOf("popcorn", "Mg Grocery Store", "dentist", "Lenskart", "Brajesh", "Facebk *6ys")
        repeat(20) { texts.forEach { model!!.predict(it) } } // warm up
        val start = System.nanoTime()
        val rounds = 200
        repeat(rounds) { texts.forEach { model!!.predict(it) } }
        val perGuessUs = (System.nanoTime() - start) / 1_000 / (rounds * texts.size)
        Log.i("CategoryModel", "load ${loadMs} ms, ${perGuessUs} µs per guess")

        assertEquals(Category.Food, CategoryGuesser.guessCasual("popcorn"))
        assertEquals(Category.Other, CategoryGuesser.guessCasual("Facebk *6ys"))
        assertTrue("a guess should take well under a millisecond, took $perGuessUs µs", perGuessUs < 1_000)
    }
}
