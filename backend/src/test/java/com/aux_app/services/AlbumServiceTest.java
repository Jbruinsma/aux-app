package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aux_app.entity.AlbumEntity;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

// AlbumService.placePiece: which album a piece ends up in, and which cover it shows. A piece always has a cover
class AlbumServiceTest {

    private final AlbumRepository albums = mock(AlbumRepository.class);
    private final AlbumService service = new AlbumService(albums, mock(ArtistRepository.class),
            mock(UserRepository.class), mock(UploadService.class), mock(TransactionTemplate.class),
            mock(ApplicationEventPublisher.class));

    private final AlbumEntity first = album("first", "A", "c_first");
    private final AlbumEntity second = album("second", "A", "c_second");
    private final AlbumEntity elsewhere = album("elsewhere", "B", "c_elsewhere");

    @BeforeEach
    void albums() {
        for (AlbumEntity a : new AlbumEntity[] {first, second, elsewhere}) {
            when(albums.findByPublicId(a.getPublicId())).thenReturn(a);
            when(albums.findById(a.getAlbumId())).thenReturn(Optional.of(a));
        }
    }

    @Test
    void joiningKeepsTheOwnCoverUnlessAsked() {
        MusicPieceEntity piece = single("c_own");
        assertNull(service.placePiece(piece, first.getPublicId(), null, null, false));
        assertEquals("first", piece.getAlbumId());
        assertEquals("c_own", piece.getCoverUrl());
        assertFalse(piece.usesAlbumCover());

        // Switching to the album cover hands back the old own cover, to delete
        assertEquals("c_own", service.placePiece(piece, null, null, true, false));
        assertEquals("c_first", piece.getCoverUrl());
        assertTrue(piece.usesAlbumCover());
    }

    @Test
    void anAlbumCoverFollowsTheMoveToAnotherAlbum() {
        MusicPieceEntity piece = inAlbum(first);
        // The old album still has its cover, but deleteImageIfUnused is what decides
        assertEquals("c_first", service.placePiece(piece, second.getPublicId(), null, null, false));
        assertEquals("second", piece.getAlbumId());
        assertEquals("c_second", piece.getCoverUrl());
        assertTrue(piece.usesAlbumCover());
    }

    @Test
    void leavingKeepsTheCoverItShows() {
        MusicPieceEntity piece = inAlbum(first);
        assertNull(service.placePiece(piece, null, true, null, false));
        assertNull(piece.getAlbumId());
        assertEquals("c_first", piece.getCoverUrl());
        assertFalse(piece.usesAlbumCover());
    }

    @Test
    void movingToAnotherArtistLeavesTheAlbum() {
        MusicPieceEntity piece = inAlbum(first);
        piece.setArtistId("B");
        assertNull(service.placePiece(piece, null, null, null, false));
        assertNull(piece.getAlbumId());
        assertEquals("c_first", piece.getCoverUrl());
        assertFalse(piece.usesAlbumCover());

        // Or joins one of the new artist's albums in the same edit
        MusicPieceEntity other = inAlbum(first);
        other.setArtistId("B");
        service.placePiece(other, elsewhere.getPublicId(), null, null, false);
        assertEquals("elsewhere", other.getAlbumId());
        assertEquals("c_elsewhere", other.getCoverUrl());
    }

    @Test
    void useAlbumCoverFalseKeepsTheImageAsTheOwnCover() {
        MusicPieceEntity piece = inAlbum(first);
        assertNull(service.placePiece(piece, null, null, false, false));
        assertEquals("first", piece.getAlbumId());
        assertEquals("c_first", piece.getCoverUrl());
        assertFalse(piece.usesAlbumCover());
    }

    @Test
    void anUploadedCoverIsLeftToReplaceMusicPieceCover() {
        MusicPieceEntity piece = inAlbum(first);
        assertNull(service.placePiece(piece, second.getPublicId(), null, null, true));
        assertEquals("second", piece.getAlbumId());
        assertEquals("c_first", piece.getCoverUrl());
    }

    @Test
    void rejectsContradictionsAndOtherArtistsAlbums() {
        assertEquals("ALBUM_ARTIST_MISMATCH", code(() -> service.placePiece(single("c"), elsewhere.getPublicId(), null, null, false)));
        assertEquals("NOT_IN_ALBUM", code(() -> service.placePiece(single("c"), null, null, true, false)));
        assertEquals("NOT_IN_ALBUM", code(() -> service.placePiece(inAlbum(first), null, true, true, false)));
        assertEquals("INVALID_FIELD", code(() -> service.placePiece(single("c"), first.getPublicId(), true, null, false)));
        assertEquals("INVALID_FIELD", code(() -> service.placePiece(inAlbum(first), null, null, true, true)));
        assertEquals("ALBUM_NOT_FOUND", code(() -> service.placePiece(single("c"), "l_nope", null, null, false)));
    }

    private static String code(Runnable call) {
        return assertThrows(AuxException.class, call::run).getDetails().code();
    }

    private static AlbumEntity album(String id, String artistId, String cover) {
        AlbumEntity album = new AlbumEntity(id, artistId, id, "maker");
        album.setCoverUrl(cover);
        return album;
    }

    private static MusicPieceEntity single(String cover) {
        MusicPieceEntity piece = new MusicPieceEntity("m", "up", "A");
        piece.setCoverUrl(cover);
        return piece;
    }

    private static MusicPieceEntity inAlbum(AlbumEntity album) {
        MusicPieceEntity piece = single(album.getCoverUrl());
        piece.setAlbumId(album.getAlbumId());
        piece.setUsesAlbumCover(true);
        return piece;
    }
}
