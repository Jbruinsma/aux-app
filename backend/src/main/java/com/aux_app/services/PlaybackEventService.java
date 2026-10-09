package com.aux_app.services;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.LastPlayback;
import com.aux_app.entity.*;
import com.aux_app.repository.*;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aux_app.dto.playlist.PlaylistMemberStatus;
import com.aux_app.error.AuxException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Plays are client-reported, so the server only credits listening time it can bound itself:
// - /stream hands out a signed, single-use play token stamped with when listening could have started
// - credited seconds are capped by the time since then and by the piece's length
// - a user's plays can't overlap in time, so one account can't log more listening than wall-clock time
// One account listening around the clock is still possible; count distinct listeners where that matters.
@Service
public class PlaybackEventService {

    // Same as Spotify: shorter listens are skips, not plays. Pieces under 30s need the whole piece.
    static final int MIN_LISTEN_SECONDS = 30;
    // Covers network delay between the end of one play and the report of the next
    static final int OVERLAP_SLACK_SECONDS = 2;
    // Different issuer from session tokens, so neither kind is accepted in place of the other
    private static final String ISSUER = "aux-play";

    private final SecretKey key;
    private final MusicPieceRepository musicPieces;
    private final PlaylistRepository playlists;
    private final PlaylistTrackRepository tracks;
    private final PlaylistMemberRepository members;
    private final PlayEventRepository playEvents;

    public PlaybackEventService(
            @Value("${aux.jwt-secret}") String secret,
            MusicPieceRepository musicPieces,
            PlaylistRepository playlists,
            PlaylistTrackRepository tracks,
            PlaylistMemberRepository members,
            PlayEventRepository playEvents
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.musicPieces = musicPieces;
        this.playlists = playlists;
        this.tracks = tracks;
        this.members = members;
        this.playEvents = playEvents;
    }

    public @Nullable LastPlayback retrieveMostRecentPlayback(String userId) {
        PlayEventRepository.LastPlaybackRow row = playEvents.findLastPlayback(userId);

        if (row == null) return null;

        return new LastPlayback(
                new MusicPieceOverview(
                        row.getMusicPieceId(),
                        row.getName(),
                        row.getCoverUrl(),
                        new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()),
                        row.getIsFavorite() == 1
                ),
                row.getPlaylistId()
        );
    }

    public String issuePlayToken(UserEntity user, MusicPieceEntity piece, @Nullable String publicPlaylistId) {
        if (publicPlaylistId != null) {
            findContextPlaylist(user, piece, publicPlaylistId);
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .id(UUID.randomUUID().toString())
                .subject(user.getPublicId())
                .claim("musicPieceId", piece.getPublicId())
                .claim("playlistId", publicPlaylistId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(UploadService.SIGNED_URL_TTL)))
                .signWith(key)
                .compact();
    }

    @Transactional
    public void recordPlay(UserEntity user, String publicMusicPieceId, String playToken, int claimedSeconds) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(playToken).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidToken();
        }
        if (!user.getPublicId().equals(claims.getSubject())
                || !publicMusicPieceId.equals(claims.get("musicPieceId", String.class))) {
            throw invalidToken();
        }
        // The primary key would also stop a reuse; checking first gives a clean error instead of a 500
        if (playEvents.existsById(claims.getId())) {
            throw new AuxException(HttpStatus.CONFLICT, "PLAY_ALREADY_RECORDED", "This play was already recorded", "playToken");
        }

        MusicPieceEntity piece = musicPieces.findByPublicId(publicMusicPieceId);
        if (piece == null || !piece.isPlayableBy(user.getUserId())) {
            throw new AuxException(HttpStatus.NOT_FOUND, "MUSIC_PIECE_NOT_FOUND", "Music piece not found", "musicPieceId");
        }
        String publicPlaylistId = claims.get("playlistId", String.class);
        PlaylistEntity playlist = publicPlaylistId == null ? null : findContextPlaylist(user, piece, publicPlaylistId);

        PlayEventEntity last = playEvents.findFirstByUserIdOrderByPlayedAtDesc(user.getUserId());
        int seconds = creditedSeconds(
                claims.getIssuedAt().toInstant(),
                Instant.now(),
                claimedSeconds,
                piece.getDurationSeconds(),
                last == null ? null : last.getPlayedAt()
        );

        PlayEventEntity event = new PlayEventEntity(claims.getId(), user.getUserId(), piece.getMusicPieceId(), seconds);
        event.setContextPlaylistId(playlist == null ? null : playlist.getPlaylistId());
        playEvents.save(event);
    }

    // The listen is the window [now - credited, now]. It must fit after the token was issued, inside the piece's
    // length, and after the user's previous play ended.
    static int creditedSeconds(Instant issuedAt, Instant now, int claimedSeconds, int pieceSeconds, @Nullable Instant lastPlayedAt) {
        long elapsed = Duration.between(issuedAt, now).toSeconds();
        int credited = (int) Math.min(Math.min(claimedSeconds, elapsed), pieceSeconds);

        if (credited < Math.min(MIN_LISTEN_SECONDS, pieceSeconds)) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "PLAY_TOO_SHORT", "Listen too short to count as a play", "listenDurationSeconds");
        }
        if (lastPlayedAt != null && now.minusSeconds(credited + OVERLAP_SLACK_SECONDS).isBefore(lastPlayedAt)) {
            throw new AuxException(HttpStatus.CONFLICT, "PLAY_OVERLAPS", "This play overlaps one already recorded", "listenDurationSeconds");
        }
        return credited;
    }

    // The playlist must contain the piece and be visible to the user; otherwise it looks missing
    private PlaylistEntity findContextPlaylist(UserEntity user, MusicPieceEntity piece, String publicPlaylistId) {
        PlaylistEntity playlist = playlists.findByPublicId(publicPlaylistId);
        if (
                playlist == null || !canView(user, playlist) || !tracks.existsById(
                        new PlaylistTrackId(
                                playlist.getPlaylistId(),
                                piece.getMusicPieceId()
                        )
                )
        ) {
            throw new AuxException(HttpStatus.NOT_FOUND, "PLAYLIST_NOT_FOUND", "Playlist not found", "playlistId");
        }
        return playlist;
    }

    private boolean canView(UserEntity user, PlaylistEntity playlist) {
        if (Boolean.TRUE.equals(playlist.getIsPublic()) || playlist.getOwnerId().equals(user.getUserId())) {
            return true;
        }
        PlaylistMemberEntity member = members.findByPlaylistIdAndUserId(playlist.getPlaylistId(), user.getUserId());
        return member != null && member.getStatus() == PlaylistMemberStatus.ACCEPTED;
    }

    private static AuxException invalidToken() {
        return new AuxException(HttpStatus.BAD_REQUEST, "INVALID_PLAY_TOKEN", "Play token is invalid or expired", "playToken");
    }
}
