package com.aux_app.services;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aux_app.entity.FollowId;
import com.aux_app.entity.PublicId;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.NotificationRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.LiveEventService.Section;

// Following a user, and the NEW_FOLLOWER notification it sends them
@Service
public class FollowService {

    private final UserRepository users;
    private final FollowRepository follows;
    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;

    public FollowService(UserRepository users, FollowRepository follows, NotificationRepository notifications,
                         ApplicationEventPublisher events) {
        this.users = users;
        this.follows = follows;
        this.notifications = notifications;
        this.events = events;
    }

    // A repeat follow does nothing. A new one notifies the followed user; if they were followed by this user before,
    // that notification moves to the top, unread, instead of a second one being added
    @Transactional
    public void follow(UserEntity follower, String username) {
        UserEntity target = users.findByUsernameIgnoreCase(username);
        if (target == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", "username");
        }
        if (target.getUserId().equals(follower.getUserId())) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "You can't follow yourself", "username");
        }
        boolean already = follows.existsById(new FollowId(follower.getUserId(), target.getUserId()));
        Instant now = Instant.now();
        follows.follow(follower.getUserId(), target.getUsername(), now);
        // Followed users leave the caller's matches
        events.publishEvent(new LiveEventService.Update(Set.of(follower.getUserId()), Set.of(Section.MATCHES)));
        if (already) {
            return;
        }
        notifications.upsertNewFollower(UUID.randomUUID().toString(), PublicId.generate(PublicId.NOTIFICATION),
                target.getUserId(), follower.getUserId(), now);
        // The caller's own NEW_FOLLOWER from the target, if any, now has followingBack true
        events.publishEvent(new LiveEventService.Update(Set.of(target.getUserId(), follower.getUserId()),
                Set.of(Section.NOTIFICATIONS)));
    }
}
