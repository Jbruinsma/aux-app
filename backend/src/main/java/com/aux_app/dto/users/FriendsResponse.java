package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One page of the caller's friends")
public record FriendsResponse(
        @Schema(description = "How many friends the caller has in all, not just on this page", example = "12") int totalResults,
        @Schema(description = "This page of friends, in order") List<Friend> friends
) {}
