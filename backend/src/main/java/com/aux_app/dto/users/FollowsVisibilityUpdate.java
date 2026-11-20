package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

// A string rather than the enum, so an unknown value is a 400 INVALID_FIELD instead of MALFORMED_BODY
@Schema(description = "Who can open the caller's followers and following lists")
public record FollowsVisibilityUpdate(
        @Schema(description = "EVERYONE, FRIENDS or NO_ONE", example = "FRIENDS", allowableValues = {"EVERYONE", "FRIENDS", "NO_ONE"})
        String followsVisibility
) {}
