package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProfanityFilterTest {

    @Test
    void masksBannedTermsOnly() {
        assertEquals("**** this", ProfanityFilter.mask("FUCK this"));
        assertEquals("***** and giggles", ProfanityFilter.mask("Shits and giggles"));
        assertEquals("say *************!", ProfanityFilter.mask("say white   power!"));
        assertEquals("classic assassin from Scunthorpe", ProfanityFilter.mask("classic assassin from Scunthorpe"));
        assertNull(ProfanityFilter.mask(null));
    }
}
