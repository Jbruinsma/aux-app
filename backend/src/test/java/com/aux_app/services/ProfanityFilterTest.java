package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfanityFilterTest {

    @Test
    void masksBannedTermsOnly() {
        assertEquals("#### this", ProfanityFilter.mask("FUCK this"));
        assertEquals("##### and giggles", ProfanityFilter.mask("Shits and giggles"));
        assertEquals("say #############!", ProfanityFilter.mask("say white   power!"));
        assertEquals("classic assassin from Scunthorpe", ProfanityFilter.mask("classic assassin from Scunthorpe"));
        assertNull(ProfanityFilter.mask(null));
    }

    @Test
    void containsTreatsUnderscoreAsWordBreak() {
        assertTrue(ProfanityFilter.contains("xx_shit_xx"));
        assertFalse(ProfanityFilter.contains("classic_dude"));
        assertFalse(ProfanityFilter.contains(null));
    }
}
