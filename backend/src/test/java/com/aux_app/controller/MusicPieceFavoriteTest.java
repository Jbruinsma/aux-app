package com.aux_app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.entity.UserFavoriteTrackEntity;
import com.aux_app.entity.UserFavoriteTrackId;
import com.aux_app.error.AuxException;
import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.PlayEventRepository;
import com.aux_app.repository.UserFavoriteTrackRepository;
import com.aux_app.services.AlbumService;
import com.aux_app.services.LiveEventService;
import com.aux_app.services.PlaybackEventService;
import com.aux_app.services.RecommendationService;
import com.aux_app.services.TagService;
import com.aux_app.services.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

// PUT/DELETE /api/music-pieces/{id}/favorite: who can favorite what, repeat calls, and the matches refresh
class MusicPieceFavoriteTest {

    private final MusicPieceRepository pieces = mock(MusicPieceRepository.class);
    private final UserFavoriteTrackRepository favorites = mock(UserFavoriteTrackRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MusicPieceController controller = new MusicPieceController(mock(UploadService.class),
            mock(ArtistRepository.class), mock(AlbumRepository.class), mock(AlbumService.class), pieces,
            mock(PlayEventRepository.class), favorites, mock(PlaybackEventService.class), mock(TagService.class),
            mock(RecommendationService.class), events);

    private final UserEntity fan = new UserEntity("fan", "f@x", "x", OnboardingStep.DONE);
    private final MusicPieceEntity open = piece("open", "up", true);
    private final MusicPieceEntity hidden = piece("hidden", "up", false);
    private final MusicPieceEntity mine = piece("mine", "fan", false);

    private static final LiveEventService.Update MATCHES =
            LiveEventService.Update.everyoneOnline(Set.of(LiveEventService.Section.MATCHES));

    @BeforeEach
    void pieces() {
        for (MusicPieceEntity p : new MusicPieceEntity[] {open, hidden, mine}) {
            when(pieces.findByPublicId(p.getPublicId())).thenReturn(p);
        }
    }

    @Test
    void favoritesAPlayablePieceOnceAndRefreshesMatches() {
        controller.favoriteMusicPiece(fan, open.getPublicId());
        verify(favorites).save(any(UserFavoriteTrackEntity.class));
        verify(events).publishEvent(MATCHES);

        // A repeat call keeps the first favorite and sends nothing
        when(favorites.existsById(new UserFavoriteTrackId("fan", "open"))).thenReturn(true);
        controller.favoriteMusicPiece(fan, open.getPublicId());
        verify(favorites).save(any(UserFavoriteTrackEntity.class));
        verify(events).publishEvent(MATCHES);
    }

    @Test
    void favoritesTheCallersOwnPrivatePiece() {
        controller.favoriteMusicPiece(fan, mine.getPublicId());
        verify(favorites).save(any(UserFavoriteTrackEntity.class));
    }

    @Test
    void someoneElsesPrivatePieceOrAMissingOneIsNotFound() {
        for (String id : new String[] {hidden.getPublicId(), "m_missing"}) {
            AuxException e = assertThrows(AuxException.class, () -> controller.favoriteMusicPiece(fan, id));
            assertEquals("MUSIC_PIECE_NOT_FOUND", e.getDetails().code());
        }
        verify(favorites, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unfavoritesEvenAfterThePieceWentPrivate() {
        UserFavoriteTrackId id = new UserFavoriteTrackId("fan", "hidden");
        when(favorites.existsById(id)).thenReturn(true);
        controller.unfavoriteMusicPiece(fan, hidden.getPublicId());
        verify(favorites).deleteById(id);
        verify(events).publishEvent(MATCHES);
    }

    @Test
    void unfavoritingSomethingThatIsntAFavoriteDoesNothing() {
        controller.unfavoriteMusicPiece(fan, open.getPublicId());
        controller.unfavoriteMusicPiece(fan, "m_missing");
        verify(favorites, never()).deleteById(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private static MusicPieceEntity piece(String id, String uploader, boolean isPublic) {
        MusicPieceEntity piece = new MusicPieceEntity(id, uploader, "A");
        piece.setIsPublic(isPublic);
        return piece;
    }
}
