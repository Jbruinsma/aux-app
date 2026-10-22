package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aux_app.error.AuxException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagServiceTest {

    @Test
    void normalizesAndDedupes() {
        assertEquals(List.of("lo-fi", "late night", "r&b"),
                TagService.normalize(List.of("  Lo-Fi ", "LATE   night", "lo-fi", "r&b")));
    }

    @Test
    void nullMeansUnchangedAndBlankMeansNone() {
        assertNull(TagService.normalize(null));
        assertEquals(List.of(), TagService.normalize(List.of("")));
        assertEquals(List.of(), TagService.normalize(List.of("  ", "")));
    }

    @Test
    void rejectsBadTags() {
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("a")));
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("x".repeat(25))));
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("chill,sad")));
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("--")));
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("shit")));
        assertThrows(AuxException.class, () -> TagService.normalize(List.of("a1", "a2", "a3", "a4", "a5", "a6")));
    }
}
