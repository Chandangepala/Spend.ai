package com.basic.spendai.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    @Test
    fun hdfcUpiSentToMerchant() {
        val txn = SmsParser.parse("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 14/10/26 Ref 628712345678 Not You? Call 18002586161")
        assertEquals(250.0, txn!!.amount, 0.001)
        assertEquals("Swiggy", txn.merchant)
    }

    @Test
    fun upiDebitToVpaUsesNameBeforeAt() {
        val txn = SmsParser.parse("Rs.250.00 debited from A/c XX1234 on 14-10-26 to VPA swiggy@icici UPI Ref 123")
        assertEquals(250.0, txn!!.amount, 0.001)
        assertEquals("Swiggy", txn.merchant)
    }

    @Test
    fun cardSpendWithCommaAmountAndAtMerchant() {
        val txn = SmsParser.parse("INR 1,299.00 spent on ICICI Bank Card XX9876 on 14-Oct-26 at AMAZON PAY. Avl Limit: INR 45,000.00")
        assertEquals(1299.0, txn!!.amount, 0.001)
        assertEquals("Amazon Pay", txn.merchant)
    }

    @Test
    fun iciciDebitNamesThePayeeThatWasCredited() {
        val txn = SmsParser.parse("ICICI Bank Acct XX123 debited for Rs 500.00 on 14-Oct-26; ZOMATO credited. UPI:628712345678. Call 18002662 for dispute.")
        assertEquals(500.0, txn!!.amount, 0.001)
        assertEquals("Zomato", txn.merchant)
    }

    @Test
    fun sbiDebitWithoutCurrencyMarker() {
        val txn = SmsParser.parse("Dear UPI user A/C X1234 debited by 20.0 on date 14Oct26 trf to RAJU TEA STALL Refno 628712345678. If not u? call 1800111109. -SBI")
        assertEquals(20.0, txn!!.amount, 0.001)
        assertEquals("Raju Tea Stall", txn.merchant)
    }

    @Test
    fun axisUpiP2mPath() {
        val txn = SmsParser.parse("INR 540.00 debited A/c no. XX1234 14-10-26 10:20:11 UPI/P2M/628712345678/UBER INDIA Not you? SMS BLOCK 1234 to 919951860002 Axis Bank")
        assertEquals(540.0, txn!!.amount, 0.001)
        assertEquals("Uber India", txn.merchant)
    }

    @Test
    fun kotakSentToVpaWithoutVpaKeyword() {
        val txn = SmsParser.parse("Sent Rs.120.00 from Kotak Bank AC X1234 to blinkit@okaxis on 14-10-26.UPI Ref 628712345678. Not you, https://kotak.com/KBANKT/Fraud")
        assertEquals(120.0, txn!!.amount, 0.001)
        assertEquals("Blinkit", txn.merchant)
    }

    @Test
    fun timeAfterAtIsNotAMerchant() {
        val txn = SmsParser.parse("Rs 300 withdrawn from A/c XX5678 at 10:30 on 14-10-26. Avl Bal Rs 12,000")
        assertEquals(300.0, txn!!.amount, 0.001)
        assertNull(txn.merchant)
    }

    @Test
    fun ignoresCredits() {
        assertNull(SmsParser.parse("Rs 50,000.00 credited to A/c XX1234 on 01-10-26 by NEFT SALARY. Avl Bal Rs 62,000"))
        assertNull(SmsParser.parse("Your A/c XX1234 is credited with Rs 500 by A/c linked to VPA friend@okhdfc (UPI Ref 123)"))
    }

    @Test
    fun ignoresOtpPromoAndUpcomingDebits() {
        assertNull(SmsParser.parse("123456 is your OTP for txn of Rs 999 on card XX1234. Do not share."))
        assertNull(SmsParser.parse("Rs 2,500 will be debited from A/c XX1234 on 20-10-26 towards EMI"))
        assertNull(SmsParser.parse("Get Rs 100 cashback when you paid with UPI this week!"))
        assertNull(SmsParser.parse("Payment of Rs 4,000 is due on 20-10-26 for card XX1234"))
        assertNull(SmsParser.parse("John has requested Rs 200 from your UPI a/c. Approve in app."))
    }

    @Test
    fun needsAnAmountAndAnAccountHint() {
        assertNull(SmsParser.parse("Your order has been paid and will ship soon"))
        assertNull(SmsParser.parse("You spent Rs 200 at the fair, hope you had fun"))
    }
}
