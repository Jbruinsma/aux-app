package com.aux_app.services;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.aux_app.dto.users.FollowListPage;
import com.aux_app.dto.users.FollowListUser;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.FollowRepository.FollowListRow;
import com.aux_app.repository.UserRepository;

// Pages through who follows a user and who they follow. Same keyset cursor idea as NotificationService.
@Service
public class FollowListService {

    static final int PAGE_SIZE = 50;

    private final UserRepository users;
    private final FollowRepository follows;

    public FollowListService(UserRepository users, FollowRepository follows) {
        this.users = users;
        this.follows = follows;
    }

    public FollowListPage followers(String username, @Nullable UserEntity viewer, @Nullable String cursor) {
        return page(username, viewer, cursor, true);
    }

    public FollowListPage following(String username, @Nullable UserEntity viewer, @Nullable String cursor) {
        return page(username, viewer, cursor, false);
    }

    private FollowListPage page(String username, @Nullable UserEntity viewer, @Nullable String cursor, boolean followers) {
        UserEntity target = users.findByUsernameIgnoreCase(username);
        if (target == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", "username");
        }
        Cursor after = cursor == null || cursor.isEmpty() ? null : decodeCursor(cursor);
        // "" matches no user id, so both flags come back false when signed out
        String viewerId = viewer == null ? "" : viewer.getUserId();
        Instant afterCreatedAt = after == null ? null : after.followedAt();
        String afterUserId = after == null ? null : after.userId();

        // One extra row tells us whether there is a next page
        List<FollowListRow> rows = followers
                ? follows.findFollowersPage(target.getUserId(), viewerId, afterCreatedAt, afterUserId, PAGE_SIZE + 1)
                : follows.findFollowingPage(target.getUserId(), viewerId, afterCreatedAt, afterUserId, PAGE_SIZE + 1);

        boolean more = rows.size() > PAGE_SIZE;
        List<FollowListRow> shown = more ? rows.subList(0, PAGE_SIZE) : rows;
        List<FollowListUser> list = shown.stream()
                .map(r -> new FollowListUser(r.user().getPublicId(), r.user().getUsername(),
                        r.user().getProfilePictureUrl(), r.isFollowing(), r.followsMe()))
                .toList();
        return new FollowListPage(list, more ? encodeCursor(shown.getLast()) : null);
    }

    // Keyset cursor: the last row's follow time and the listed user's internal id. Base64 so clients treat it as opaque.
    record Cursor(Instant followedAt, String userId) {}

    static String encodeCursor(FollowListRow last) {
        String raw = last.followedAt() + "|" + last.user().getUserId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length == 2 && !parts[1].isEmpty()) {
                return new Cursor(Instant.parse(parts[0]), parts[1]);
            }
        } catch (IllegalArgumentException | DateTimeException ignored) {
            // Bad base64 or timestamp; falls through to the 400
        }
        throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "Invalid cursor", "cursor");
    }
}
