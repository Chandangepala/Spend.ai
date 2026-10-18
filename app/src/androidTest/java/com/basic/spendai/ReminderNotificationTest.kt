package com.basic.spendai

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.reminders.ReminderMessage
import com.basic.spendai.reminders.ReminderReceiver
import com.basic.spendai.reminders.ReminderTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Posts a sample daily reminder (no app data involved) and checks what the user would see. */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun clear() = ReminderReceiver.dismiss(context)

    @Test
    fun reviewReminderOffersAddToo() {
        assumeTrue("notifications are off for SpendAI", manager.areNotificationsEnabled())
        ReminderReceiver.notify(context, ReminderMessage("2 spends to review", "₹1,240.00 from your bank SMS, waiting for you to confirm.", ReminderTarget.REVIEW))
        // Posting is asynchronous; give the system a moment to list it.
        val shown = (1..20).firstNotNullOf { _ ->
            manager.activeNotifications.firstOrNull { it.id == ReminderReceiver.NOTIFICATION_ID } ?: run { Thread.sleep(100); null }
        }.notification
        assertEquals("2 spends to review", shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("daily_reminder", shown.channelId)
        assertEquals(listOf("Add expense"), shown.actions.map { it.title.toString() })
    }
}
