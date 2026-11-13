package com.aux_app.services;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.Friend;
import com.aux_app.dto.users.FriendListen;
import com.aux_app.dto.users.FriendsResponse;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.MusicPieceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Friends are users who follow each other. The order mixes the database (latest plays) with live state (who's online,
// what's playing), so every friend is loaded and sorted here, then one page is cut out.
// ponytail: loads all of a user's friends per page; fine until someone has thousands
@Service
public class FriendsService {

    // Online first, then the most recent listen (playing now or last played), friends with none last, then username
    static final Comparator<Friend> ORDER = Comparator
            .comparing(Friend::isOnline, Comparator.reverseOrder())
            .thenComparing(FriendsService::latestListen, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Friend::username, String.CASE_INSENSITIVE_ORDER);

    private final FollowRepository follows;
    private final MusicPieceRepository musicPieces;
    private final LiveEventService liveEvents;
    private final NowPlayingService nowPlaying;

    public FriendsService(FollowRepository follows, MusicPieceRepository musicPieces, LiveEventService liveEvents,
                          NowPlayingService nowPlaying) {
        this.follows = follows;
        this.musicPieces = musicPieces;
        this.liveEvents = liveEvents;
        this.nowPlaying = nowPlaying;
    }

    // Publishes the caller's player state for their friends. An unknown or private piece just clears it, so the
    // caller learns nothing about pieces they can't see and the player never has to handle an error
    public void updateNowPlaying(UserEntity user, String publicMusicPieceId, int positionSeconds, boolean isPlaying) {
        MusicPieceEntity piece = musicPieces.findByPublicId(publicMusicPieceId);
        if (piece == null) {
            nowPlaying.clear(user.getUserId());
            return;
        }
        nowPlaying.update(user.getUserId(), piece, positionSeconds, isPlaying);
    }

    public FriendsResponse list(UserEntity user, int limit, int offset) {
        List<FollowRepository.FriendRow> rows = follows.findFriends(user.getUserId());

        // Who's online and playing, read once per friend so the sort sees one consistent snapshot
        record Live(FollowRepository.FriendRow row, boolean online, NowPlayingService.@Nullable Listening listening) {}
        List<Live> live = rows.stream().map(row -> {
            boolean online = liveEvents.isOnline(row.getInternalUserId());
            return new Live(row, online, online ? nowPlaying.current(row.getInternalUserId()) : null);
        }).toList();

        Set<String> pieceIds = new HashSet<>();
        for (Live l : live) {
            if (!l.row().getMusicPieceId().isEmpty()) pieceIds.add(l.row().getMusicPieceId());
            if (l.listening() != null) pieceIds.add(l.listening().publicMusicPieceId());
        }
        Map<String, MusicPieceOverview> pieces = musicPieces.findOverviews(List.copyOf(pieceIds), user.getUserId())
                .stream().collect(Collectors.toMap(MusicPieceOverview::musicPieceId, Function.identity()));

        List<Friend> friends = live.stream().map(l -> {
            FollowRepository.FriendRow row = l.row();
            FriendListen now = l.listening() == null ? null
                    : listen(pieces.get(l.listening().publicMusicPieceId()), l.listening().startedAt());
            FriendListen last = row.getMusicPieceId().isEmpty() ? null
                    : listen(pieces.get(row.getMusicPieceId()), Instant.ofEpochMilli(row.getPlayedAt()));
            return new Friend(row.getUserId(), row.getUsername(), row.getPfpUrl(), l.online(), now, last);
        }).sorted(ORDER).toList();

        int from = Math.min(offset, friends.size());
        return new FriendsResponse(friends.size(), friends.subList(from, Math.min(from + limit, friends.size())));
    }

    // Null if the piece was made private between reading the play and loading the piece
    private static @Nullable FriendListen listen(@Nullable MusicPieceOverview piece, Instant at) {
        return piece == null || !piece.isPublic() ? null : new FriendListen(piece, at);
    }

    private static @Nullable Instant latestListen(Friend friend) {
        if (friend.nowPlaying() != null) return friend.nowPlaying().at();
        return friend.lastPlayed() == null ? null : friend.lastPlayed().at();
    }
}
