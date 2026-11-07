package com.aux_app.services;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.CompatibilityMatch;
import com.aux_app.dto.users.MatchesResponse;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.PlayEventRepository;
import com.aux_app.repository.UserRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Musical compatibility between two listeners, 0-100. Drawn out with a worked example in docs/compatibility.html.
//
// Each user's taste is two weight maps built by PlayEventRepository.ARTIST_WEIGHTS and TAG_WEIGHTS: one per artist,
// one per curated tag, from counted plays that halve in weight every HALF_LIFE, plus favorites. A pair then scores:
//   - sharing at least one artist: 100 * (0.7 * cos(artists) + 0.3 * cos(tags)) * n / (n + K), n = shared artists
//   - else sharing a curated tag:  100 * cos(tags) * m / (m + K), m = shared tags, at most TAG_ONLY_CAP
//   - else 0
// n / (n + K) keeps two people who happen to share one song from scoring Super.
@Service
public class CompatibilityService {

    static final double ARTIST_SHARE = 0.7; // tags get the rest
    static final int K = 1;
    // Tag-only matches stay at Medium or below
    static final int TAG_ONLY_CAP = 39;
    static final Duration HALF_LIFE = Duration.ofDays(90);
    static final int MATCHES_LIMIT = 20;
    static final int SHARED_LIMIT = 3;

    private final PlayEventRepository playEvents;
    private final UserRepository users;
    private final FollowRepository follows;

    public CompatibilityService(PlayEventRepository playEvents, UserRepository users, FollowRepository follows) {
        this.playEvents = playEvents;
        this.users = users;
        this.follows = follows;
    }

    // Artist weights keyed by artist public id, tag weights by tag
    record Taste(Map<String, Double> artists, Map<String, Double> tags) {}

    // Shared keys hold every shared item, strongest first; responses keep the first SHARED_LIMIT
    record Score(int compatibility, List<String> sharedArtists, List<String> sharedTags) {
        boolean sharesAnything() {
            return !sharedArtists.isEmpty() || !sharedTags.isEmpty();
        }
    }

    private record Tastes(Map<String, Taste> byUser, Map<String, ArtistSummary> artists) {}

    // For the `matches` live event. Leaves out the caller and users they follow. Best first; ties go to more shared
    // artists. Empty when the caller has no plays or favorites.
    // ponytail: loads every user's taste on each call, and LiveEventService calls this once per online user after
    // every play. Cache the Tastes per update (or precompute) once that gets slow.
    public MatchesResponse matches(String userId) {
        Tastes tastes = load(null, null);
        Taste mine = tastes.byUser().get(userId);
        if (mine == null) {
            return new MatchesResponse(List.of());
        }

        Set<String> skip = new HashSet<>(follows.findFollowingIds(userId));
        skip.add(userId);

        List<Map.Entry<String, Score>> scored = new ArrayList<>();
        tastes.byUser().forEach((otherId, theirs) -> {
            if (skip.contains(otherId)) return;
            Score s = score(mine, theirs);
            if (s.sharesAnything()) scored.add(Map.entry(otherId, s));
        });
        scored.sort(Comparator.<Map.Entry<String, Score>>comparingInt(e -> e.getValue().compatibility()).reversed()
                .thenComparing(e -> e.getValue().sharedArtists().size(), Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey));
        List<Map.Entry<String, Score>> top = scored.subList(0, Math.min(MATCHES_LIMIT, scored.size()));

        Map<String, UserEntity> people = users.findAllById(top.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        return new MatchesResponse(top.stream()
                .filter(e -> people.containsKey(e.getKey()))
                .map(e -> toMatch(people.get(e.getKey()), e.getValue(), tastes.artists()))
                .toList());
    }

    // For a profile: the viewer's compatibility with its owner. Null when either has no plays or favorites yet.
    public @Nullable CompatibilityMatch pair(String viewerId, UserEntity owner) {
        Tastes tastes = load(viewerId, owner.getUserId());
        Taste mine = tastes.byUser().get(viewerId);
        Taste theirs = tastes.byUser().get(owner.getUserId());
        if (mine == null || theirs == null) {
            return null;
        }
        return toMatch(owner, score(mine, theirs), tastes.artists());
    }

    static Score score(Taste a, Taste b) {
        List<String> sharedArtists = shared(a.artists(), b.artists());
        List<String> sharedTags = shared(a.tags(), b.tags());
        long compatibility;
        if (!sharedArtists.isEmpty()) {
            double raw = ARTIST_SHARE * cosine(a.artists(), b.artists()) + (1 - ARTIST_SHARE) * cosine(a.tags(), b.tags());
            compatibility = Math.round(100 * raw * confidence(sharedArtists.size()));
        } else if (!sharedTags.isEmpty()) {
            compatibility = Math.min(TAG_ONLY_CAP, Math.round(100 * cosine(a.tags(), b.tags()) * confidence(sharedTags.size())));
        } else {
            compatibility = 0;
        }
        return new Score((int) compatibility, sharedArtists, sharedTags);
    }

    private static double confidence(int shared) {
        return (double) shared / (shared + K);
    }

    // Keys both have, the one both care about most first: ranked by the smaller of the two weights
    static List<String> shared(Map<String, Double> a, Map<String, Double> b) {
        return a.keySet().stream()
                .filter(b::containsKey)
                .sorted(Comparator.comparingDouble((String key) -> Math.min(a.get(key), b.get(key))).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
        }
        double norms = norm(a) * norm(b);
        return norms == 0 ? 0 : dot / norms;
    }

    private static double norm(Map<String, Double> weights) {
        return Math.sqrt(weights.values().stream().mapToDouble(w -> w * w).sum());
    }

    // userA null loads every user
    private Tastes load(@Nullable String userA, @Nullable String userB) {
        long now = Instant.now().toEpochMilli();
        double halfLife = HALF_LIFE.toMillis();
        Map<String, Taste> byUser = new HashMap<>();
        Map<String, ArtistSummary> artists = new HashMap<>();

        for (PlayEventRepository.ArtistWeightRow row : playEvents.findArtistWeights(now, halfLife, userA, userB)) {
            taste(byUser, row.getUserId()).artists().put(row.getArtistId(), row.getWeight());
            artists.putIfAbsent(row.getArtistId(),
                    new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()));
        }
        for (PlayEventRepository.TagWeightRow row : playEvents.findTagWeights(now, halfLife, userA, userB)) {
            taste(byUser, row.getUserId()).tags().put(row.getTag(), row.getWeight());
        }
        return new Tastes(byUser, artists);
    }

    private static Taste taste(Map<String, Taste> byUser, String userId) {
        return byUser.computeIfAbsent(userId, id -> new Taste(new HashMap<>(), new HashMap<>()));
    }

    private static CompatibilityMatch toMatch(UserEntity user, Score score, Map<String, ArtistSummary> artists) {
        return new CompatibilityMatch(
                new PlaylistOwner(user.getPublicId(), user.getProfilePictureUrl(), user.getUsername()),
                score.compatibility(),
                score.sharedArtists().stream().limit(SHARED_LIMIT).map(artists::get).toList(),
                score.sharedTags().stream().limit(SHARED_LIMIT).toList()
        );
    }
}
