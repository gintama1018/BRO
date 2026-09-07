package com.gintama.novabrowser.security

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaBiometricHelperTest {

    @Test
    fun `biometric preferences keys are correctly defined`() {
        assertEquals("pref_biometric_private_tabs", NovaBiometricHelper.PREF_LOCK_PRIVATE_TABS)
        assertEquals("pref_biometric_app_lock", NovaBiometricHelper.PREF_LOCK_APP_LAUNCH)
    }
}
