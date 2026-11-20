package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.FollowId;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.NotificationRepository;
import com.aux_app.repository.UserRepository;

class FollowServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final FollowService service = new FollowService(users, follows, notifications, events);

    private final UserEntity me = new UserEntity("me", "me@x", "x", OnboardingStep.DONE);
    private final UserEntity amy = new UserEntity("amy", "a@x", "x", OnboardingStep.DONE);

    @Test
    void newFollowNotifiesTheFollowedUser() {
        when(users.findByUsernameIgnoreCase("Amy")).thenReturn(amy);

        service.follow(me, "Amy");

        verify(follows).follow(eq("me"), eq(amy.getUsername()), any());
        verify(notifications).upsertNewFollower(anyString(), anyString(), eq("amy"), eq("me"), any());
    }

    @Test
    void repeatFollowSendsNoNotification() {
        when(users.findByUsernameIgnoreCase("amy")).thenReturn(amy);
        when(follows.existsById(new FollowId("me", "amy"))).thenReturn(true);

        service.follow(me, "amy");

        verify(notifications, never()).upsertNewFollower(any(), any(), any(), any(), any());
    }

    @Test
    void followingYourselfOrNobodyFails() {
        when(users.findByUsernameIgnoreCase("me")).thenReturn(me);

        assertEquals(400, assertThrows(AuxException.class, () -> service.follow(me, "me")).getStatus().value());
        assertEquals(404, assertThrows(AuxException.class, () -> service.follow(me, "nobody")).getStatus().value());
        verify(follows, never()).follow(any(), any(), any());
    }
}
