package com.slam.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slam.app.data.AccountIdentity
import com.slam.app.data.AccountStateWiper
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.security.PinStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun cleanUp() = runBlocking {
        listOf("test-account-a", "test-account-b").forEach {
            AccountStateWiper(context).wipe(it)
        }
    }

    @Test
    fun wipingAccountRemovesPinContactsAndDatabase() = runBlocking {
        AccountIdentity.setCurrent(context, "test-account-a")
        assertTrue(PinStore(context).setPin("1234"))
        SlamDatabase.get(context).trustedNumbers().insert(
            TrustedNumberEntity(
                label = "Owner",
                number = "+923001234567",
                normalized = "3001234567",
            ),
        )

        AccountStateWiper(context).wipe("test-account-a")
        AccountIdentity.setCurrent(context, "test-account-b")

        assertFalse(PinStore(context).hasPin())
        assertTrue(SlamDatabase.get(context).trustedNumbers().all().isEmpty())
    }
}
