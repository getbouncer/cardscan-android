package com.getbouncer.scan.payment

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.payment.card.CardType
import com.getbouncer.scan.payment.card.getCardType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class PaymentCardAndroidTest {

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun paymentCardTypeDebit() = runBlocking {
        assertEquals(CardType.Debit, getCardType(appContext, "349011"))
        assertEquals(CardType.Credit, getCardType(appContext, "648298"))
        assertEquals(CardType.Credit, getCardType(appContext, "648299"))
        assertEquals(CardType.Prepaid, getCardType(appContext, "531306"))
        assertEquals(CardType.Unknown, getCardType(appContext, "123456"))
    }
}
