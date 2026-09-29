package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnboardingServiceTest {

    @Test
    void reservedUsernamesIgnoreCase() {
        assertTrue(OnboardingService.isReserved("Admin"));
        assertTrue(OnboardingService.isReserved("SETTINGS"));
        assertFalse(OnboardingService.isReserved("admin2"));
    }
}
