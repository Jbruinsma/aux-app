package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

// Stored in users.follows_visibility
@Schema(description = "Who can open the user's followers and following lists. EVERYONE (default); FRIENDS, people who follow each other with the user; NO_ONE, only the user. The counts show to everyone either way")
public enum FollowsVisibility {
    EVERYONE,
    FRIENDS,
    NO_ONE;

    // Whether a viewer may open the owner's lists. The owner always can; a signed-out viewer is neither
    public boolean allows(boolean isOwner, boolean viewerFollowsOwner, boolean ownerFollowsViewer) {
        return isOwner || switch (this) {
            case EVERYONE -> true;
            case FRIENDS -> viewerFollowsOwner && ownerFollowsViewer;
            case NO_ONE -> false;
        };
    }
}
