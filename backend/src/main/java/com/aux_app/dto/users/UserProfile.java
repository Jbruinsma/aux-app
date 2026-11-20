package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.album.AlbumOverview;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.playlist.ProfilePlaylist;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Schema(description = "A user's public profile and the caller's relation to them")
public record UserProfile(
        @Schema(description = "Username", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(description = "Banner URL", example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        @Schema(description = "Optional profile fields") ProfileDetails profileDetails,
        @Schema(description = "The user's playlists: public ones, plus private ones when the caller is the owner") List<ProfilePlaylist> playlists,
        @Schema(description = "Up to 10 pieces the user played most recently, newest first, one row per piece from its latest play. Only pieces the caller can play are listed; `isFavorite` and the playlist (left off when the caller can't see it) are the caller's view. Empty if none") List<LastPlayback> recentPlays,
        @Schema(description = "Up to 10 artists the user played most, all time, ties going to the one played most recently. Only plays of pieces the caller can play count. Empty if none") List<ArtistSummary> topArtists,
        @Schema(description = "Up to 10 albums the user played most, as in `GET /api/albums/top/{username}`. Empty if none") List<AlbumOverview> topAlbums,
        @Schema(description = "The user's favorite artists, newest favorite first, as in `GET /api/artists/favorites/{username}`. Empty if none") List<ArtistSummary> favoriteArtists,
        @Schema(description = "Whether the caller owns this profile", example = "false") boolean isMe,
        @Schema(description = "Whether the caller follows this user", example = "true") boolean isFollowing,
        @Schema(description = "Whether this user follows the caller", example = "false") boolean followingMe,
        @Schema(description = "Whether the caller may open this user's followers and following lists (`GET /api/users/{username}/followers` and `.../following`), as their `followsVisibility` decides. Always true on the caller's own profile", example = "true") boolean canViewFollows,
        @Schema(description = "How many users follow this user: exact up to 999, then rounded down (1.2K, 15.8K, 3.4M)", example = "1.2K") String followersCount,
        @Schema(description = "How many users this user follows: exact up to 999, then rounded down (1.2K, 15.8K, 3.4M)", example = "856") String followingCount,
        @Schema(description = "Uploads the caller can see (public ones, plus private ones on the caller's own profile): exact up to 999, then rounded down (1.2K, 15.8K, 3.4M)", example = "5") String uploadsCount,
        @Schema(description = "The caller's compatibility with this user, 0-100 (levels as in `CompatibilityMatch`). Null when signed out, on the caller's own profile, or when either of them has no plays or favorites yet; 0 when they share nothing", example = "87") Integer compatibility,
        @Schema(description = "Up to 3 artists both listen to, the one both play most first. Null when `compatibility` is") List<ArtistSummary> sharedArtists,
        @Schema(description = "Up to 3 curated tags both listen to, strongest first. Null when `compatibility` is", example = "[\"r&b\", \"chill\"]") List<String> sharedTags
) {
    // The listening sections and compatibility, which ProfileListeningService loads and caches. A null match leaves
    // compatibility, sharedArtists and sharedTags null
    public UserProfile withListening(List<LastPlayback> recentPlays, List<ArtistSummary> topArtists,
                                     List<AlbumOverview> topAlbums, List<ArtistSummary> favoriteArtists,
                                     CompatibilityMatch match) {
        return new UserProfile(username, pfpUrl, bannerUrl, profileDetails, playlists,
                recentPlays, topArtists, topAlbums, favoriteArtists, isMe, isFollowing, followingMe, canViewFollows,
                followersCount, followingCount, uploadsCount,
                match == null ? null : match.compatibility(),
                match == null ? null : match.sharedArtists(),
                match == null ? null : match.sharedTags());
    }

    public static String compactCount(int count) {
        NumberFormat format = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        format.setRoundingMode(RoundingMode.DOWN);
        return format.format(count);
    }
}
