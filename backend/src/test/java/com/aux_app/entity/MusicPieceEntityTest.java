package com.aux_app.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MusicPieceEntityTest {

    @Test
    void privatePieceIsPlayableOnlyByUploader() {
        MusicPieceEntity piece = new MusicPieceEntity("m1", "owner", "a1");
        assertTrue(piece.isPlayableBy("owner"));
        assertFalse(piece.isPlayableBy("someone-else"));
        assertFalse(piece.isPlayableBy(null));

        piece.setIsPublic(true);
        assertTrue(piece.isPlayableBy("someone-else"));
        assertTrue(piece.isPlayableBy(null));
    }
}
