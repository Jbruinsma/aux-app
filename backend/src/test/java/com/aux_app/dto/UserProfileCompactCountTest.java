package com.aux_app.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aux_app.dto.users.UserProfile;
import org.junit.jupiter.api.Test;

class UserProfileCompactCountTest {

    @Test
    void exactUpTo999ThenRoundedDown() {
        assertEquals("0", UserProfile.compactCount(0));
        assertEquals("999", UserProfile.compactCount(999));
        assertEquals("1K", UserProfile.compactCount(1000));
        assertEquals("1.2K", UserProfile.compactCount(1250));
        assertEquals("1.9K", UserProfile.compactCount(1999));
        assertEquals("15.8K", UserProfile.compactCount(15_800));
        assertEquals("15K", UserProfile.compactCount(15_049));
        assertEquals("999.9K", UserProfile.compactCount(999_999));
        assertEquals("3.4M", UserProfile.compactCount(3_456_789));
    }
}
