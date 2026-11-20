package com.aux_app.dto.users;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "One page of a user's followers or following, newest follow first")
public record FollowListPage(
        List<FollowListUser> users,
        @Schema(description = "Pass as `cursor` for the next page; null on the last page", nullable = true) @Nullable String nextCursor
) {}
