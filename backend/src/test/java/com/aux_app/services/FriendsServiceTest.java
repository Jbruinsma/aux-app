package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.Friend;
import com.aux_app.dto.users.FriendsResponse;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.MusicPieceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class FriendsServiceTest {

    @Test
    void onlineFirstThenLatestListenThenName() {
        FollowRepository follows = mock(FollowRepository.class);
        MusicPieceRepository musicPieces = mock(MusicPieceRepository.class);
        LiveEventService live = mock(LiveEventService.class);
        NowPlayingService nowPlaying = new NowPlayingService();

        List<FollowRepository.FriendRow> rows = List.of(
                row("old", "m_a", 1_000), // offline, played long ago
                row("none", "", 0), // offline, never played
                row("recent", "m_a", 5_000), // offline, played recently
                row("Zed", "", 0), // online, nothing playing
                row("dj", "m_a", 1_000), // online and playing m_b
                row("ghost", "", 0)); // offline, but a stale entry says m_b: hidden
        when(follows.findFriends("me")).thenReturn(rows);
        when(live.isOnline("i_Zed")).thenReturn(true);
        when(live.isOnline("i_dj")).thenReturn(true);
        MusicPieceEntity playing = piece("m_b");
        nowPlaying.update("i_dj", playing, 0, true);
        nowPlaying.update("i_ghost", playing, 0, true);
        when(musicPieces.findOverviews(anyList(), any())).thenReturn(List.of(overview("m_a"), overview("m_b")));

        FriendsService service = new FriendsService(follows, musicPieces, live, nowPlaying);
        UserEntity me = user();
        FriendsResponse all = service.list(me, 50, 0);

        assertEquals(6, all.totalResults());
        assertEquals(List.of("dj", "Zed", "recent", "old", "ghost", "none"),
                all.friends().stream().map(Friend::username).toList());
        Friend dj = all.friends().getFirst();
        assertEquals("m_b", dj.nowPlaying().musicPiece().musicPieceId());
        assertEquals("m_a", dj.lastPlayed().musicPiece().musicPieceId());
        assertNull(all.friends().get(4).nowPlaying());
        assertEquals(Instant.ofEpochMilli(5_000), all.friends().get(2).lastPlayed().at());

        assertEquals(List.of("recent", "old"),
                service.list(me, 2, 2).friends().stream().map(Friend::username).toList());
        assertEquals(List.of(), service.list(me, 2, 10).friends());
    }

    private static FollowRepository.FriendRow row(String name, String pieceId, long playedAt) {
        FollowRepository.FriendRow row = mock(FollowRepository.FriendRow.class);
        when(row.getInternalUserId()).thenReturn("i_" + name);
        when(row.getUserId()).thenReturn("u_" + name);
        when(row.getUsername()).thenReturn(name);
        when(row.getMusicPieceId()).thenReturn(pieceId);
        when(row.getPlayedAt()).thenReturn(playedAt);
        return row;
    }

    private static MusicPieceEntity piece(String publicId) {
        MusicPieceEntity piece = mock(MusicPieceEntity.class);
        when(piece.getIsPublic()).thenReturn(true);
        when(piece.getDurationSeconds()).thenReturn(200);
        when(piece.getPublicId()).thenReturn(publicId);
        return piece;
    }

    private static MusicPieceOverview overview(String id) {
        return new MusicPieceOverview(id, id, null, new ArtistSummary("a", "A", null), null, List.of(), true, false, true);
    }

    private static UserEntity user() {
        UserEntity user = mock(UserEntity.class);
        when(user.getUserId()).thenReturn("me");
        return user;
    }
}
