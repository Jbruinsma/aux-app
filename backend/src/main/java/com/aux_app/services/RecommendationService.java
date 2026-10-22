package com.aux_app.services;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.MusicPieceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    static final int DISCOVER_LIMIT = 100;
    static final int SAME_ARTIST_CAP = 3;
    // Pieces the caller played this recently are left out, so results feel new
    static final Duration RECENTLY_PLAYED = Duration.ofDays(7);
    // Window for the "most played" fill-in when too few pieces match
    static final Duration POPULAR_WINDOW = Duration.ofDays(30);

    private final MusicPieceRepository musicPieces;

    public RecommendationService(MusicPieceRepository musicPieces) {
        this.musicPieces = musicPieces;
    }

    // Public pieces like the seed (MusicPieceRepository.DISCOVER), best first. Leaves out the seed, the caller's
    // own uploads and favorites, and pieces they played recently. Caller checks the seed is playable.
    public List<MusicPieceOverview> discover(MusicPieceEntity seed, UserEntity user) {
        Instant now = Instant.now();
        List<String> ids = musicPieces.findDiscoverIds(
                seed.getMusicPieceId(),
                seed.getArtistId(),
                user.getUserId(),
                now.minus(RECENTLY_PLAYED).toEpochMilli(),
                now.minus(POPULAR_WINDOW).toEpochMilli(),
                SAME_ARTIST_CAP,
                DISCOVER_LIMIT
        );

        // findOverviews returns rows in any order; put them back in ranked order
        Map<String, MusicPieceOverview> byId = musicPieces.findOverviews(ids, user.getUserId()).stream()
                .collect(Collectors.toMap(MusicPieceOverview::musicPieceId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
