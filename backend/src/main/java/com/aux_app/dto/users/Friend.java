package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A friend of the caller (they follow each other) and what they're listening to")
public record Friend(
        @Schema(description = "Public id of the user", example = "u_41x9Rb") String userId,
        @Schema(description = "Username", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(description = "Whether they have Aux open right now", example = "true") boolean isOnline,
        @Schema(description = "The public piece their player says is playing now; null when they're offline, paused, or playing a private piece", nullable = true) FriendListen nowPlaying,
        @Schema(description = "Their latest counted play of a public piece; null if they have none", nullable = true) FriendListen lastPlayed
) {}
