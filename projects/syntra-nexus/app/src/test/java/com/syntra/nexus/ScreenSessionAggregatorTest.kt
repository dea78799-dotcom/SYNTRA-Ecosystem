package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenSessionAggregatorTest {
    @Test
    fun sumsMultipleScreenSessions() {
        val events = listOf(
            ScreenSessionAggregator.Event(100L, true),
            ScreenSessionAggregator.Event(250L, false),
            ScreenSessionAggregator.Event(400L, true),
            ScreenSessionAggregator.Event(700L, false)
        )
        assertEquals(450L, ScreenSessionAggregator.totalInteractiveMillis(events, 0L, 1_000L))
    }

    @Test
    fun closesOpenSessionAtPeriodEnd() {
        val events = listOf(ScreenSessionAggregator.Event(300L, true))
        assertEquals(700L, ScreenSessionAggregator.totalInteractiveMillis(events, 0L, 1_000L))
    }

    @Test
    fun ignoresDuplicateStateEvents() {
        val events = listOf(
            ScreenSessionAggregator.Event(100L, true),
            ScreenSessionAggregator.Event(200L, true),
            ScreenSessionAggregator.Event(500L, false),
            ScreenSessionAggregator.Event(600L, false)
        )
        assertEquals(400L, ScreenSessionAggregator.totalInteractiveMillis(events, 0L, 1_000L))
    }

    @Test
    fun handlesEmptyTimeline() {
        assertEquals(0L, ScreenSessionAggregator.totalInteractiveMillis(emptyList(), 0L, 1_000L))
    }
}
