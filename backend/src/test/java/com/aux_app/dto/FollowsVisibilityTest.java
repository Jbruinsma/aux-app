package com.aux_app.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aux_app.dto.users.FollowsVisibility;

class FollowsVisibilityTest {

    @Test
    void ownerAlwaysSeesTheirOwnLists() {
        for (FollowsVisibility visibility : FollowsVisibility.values()) {
            assertTrue(visibility.allows(true, false, false), visibility.name());
        }
    }

    @Test
    void everyoneLetsStrangersAndSignedOutViewersIn() {
        assertTrue(FollowsVisibility.EVERYONE.allows(false, false, false));
    }

    @Test
    void friendsNeedsFollowsBothWays() {
        assertTrue(FollowsVisibility.FRIENDS.allows(false, true, true));
        assertFalse(FollowsVisibility.FRIENDS.allows(false, true, false));
        assertFalse(FollowsVisibility.FRIENDS.allows(false, false, true));
        assertFalse(FollowsVisibility.FRIENDS.allows(false, false, false));
    }

    @Test
    void noOneLetsOnlyTheOwnerIn() {
        assertFalse(FollowsVisibility.NO_ONE.allows(false, true, true));
    }
}
