package com.aux_app.services;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.aux_app.dto.album.AlbumOverview;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.CompatibilityMatch;
import com.aux_app.dto.users.LastPlayback;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.PlayEventRepository;
import com.aux_app.repository.UserRepository;

// The listening part of a profile: recent plays, top artists and albums, favorite artists, and the viewer's
// compatibility with the owner. These are the profile's heavy queries and don't change on a follow, so they're cached
// per (owner, viewer) and the page can reload the profile for new counts cheaply.
// Entries for a user go as soon as their plays or favorites change (publish Changed), so their own profile is always
// current; other changes (a piece made private, an album renamed, playlist membership) show within TTL.
@Service
public class ProfileListeningService {

    static final Duration TTL = Duration.ofMinutes(2);
    static final int MAX_ENTRIES = 5_000;
    static final int RECENT_PLAYS = 10;

    public record Listening(
            List<LastPlayback> recentPlays,
            List<ArtistSummary> topArtists,
            List<AlbumOverview> topAlbums,
            List<ArtistSummary> favoriteArtists,
            @Nullable CompatibilityMatch compatibility
    ) {}

    // Publish when a user's plays or favorites change. Applied once the transaction commits
    public record Changed(String userId) {}

    // viewerId is "" when signed out
    private record Key(String ownerId, String viewerId) {}

    private final CachedMap<Key, Listening> cache = new CachedMap<>(TTL, MAX_ENTRIES);
    private final PlayEventRepository playEvents;
    private final UserRepository users;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final CompatibilityService compatibility;

    public ProfileListeningService(
            PlayEventRepository playEvents,
            UserRepository users,
            AlbumRepository albums,
            ArtistRepository artists,
            CompatibilityService compatibility
    ) {
        this.playEvents = playEvents;
        this.users = users;
        this.albums = albums;
        this.artists = artists;
        this.compatibility = compatibility;
    }

    public Listening get(UserEntity owner, @Nullable UserEntity viewer) {
        Key key = new Key(owner.getUserId(), viewer == null ? "" : viewer.getUserId());
        return cache.get(key, k -> load(owner, viewer));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(Changed changed) {
        String userId = changed.userId();
        cache.invalidate(key -> key.ownerId().equals(userId) || key.viewerId().equals(userId));
    }

    private Listening load(UserEntity owner, @Nullable UserEntity viewer) {
        String ownerId = owner.getUserId();
        String viewerId = viewer == null ? null : viewer.getUserId();
        return new Listening(
                playEvents.findRecentPlays(ownerId, viewerId, RECENT_PLAYS).stream()
                        .map(PlaybackEventService::toLastPlayback).toList(),
                users.findProfileTopArtists(ownerId, viewerId),
                albums.findTopAlbums(ownerId, viewerId),
                artists.findFavoritesOf(ownerId).stream()
                        .map(a -> new ArtistSummary(a.getPublicId(), a.getArtistName(), a.getArtistPfpUrl()))
                        .toList(),
                viewerId == null || viewerId.equals(ownerId) ? null : compatibility.pair(viewerId, owner)
        );
    }
}
