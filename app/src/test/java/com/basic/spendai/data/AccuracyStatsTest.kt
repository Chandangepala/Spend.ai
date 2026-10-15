package com.basic.spendai.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccuracyStatsTest {

    @Test
    fun roundsToNearestPercent() {
        assertEquals(87, AccuracyStats(total = 23, unchanged = 20).percent)
        assertEquals(67, AccuracyStats(total = 3, unchanged = 2).percent)
        assertEquals(100, AccuracyStats(total = 4, unchanged = 4).percent)
    }

    @Test
    fun noReviewsIsZeroNotACrash() {
        assertEquals(0, AccuracyStats(total = 0, unchanged = 0).percent)
    }
}
