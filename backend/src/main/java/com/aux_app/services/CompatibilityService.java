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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

    // Artist weights keyed by artist public id, tag weights by tag. The norms are worked out once, not per pair
    record Taste(Map<String, Double> artists, Map<String, Double> tags, double artistNorm, double tagNorm) {
        Taste(Map<String, Double> artists, Map<String, Double> tags) {
            this(artists, tags, norm(artists), norm(tags));
        }
    }

    // Shared keys hold every shared item, strongest first; responses keep the first SHARED_LIMIT
    record Score(int compatibility, List<String> sharedArtists, List<String> sharedTags) {
        boolean sharesAnything() {
            return !sharedArtists.isEmpty() || !sharedTags.isEmpty();
        }
    }

    // index is null for a pair() load
    private record Tastes(Map<String, Taste> byUser, Map<String, ArtistSummary> artists, @Nullable Index index) {}

    // The same tastes as arrays, so matches() can score the caller against everyone without hashing strings:
    // tags as a dense vector (only curated tags count, so it's short), artists as sorted ids with their weights.
    // A weight is never 0 (ln(1 + plays) plus favorites), so a 0 in the tag vector means the tag isn't there.
    private record Index(String[] userIds, Map<String, Integer> positions, double[][] tags, double[] tagNorms,
                         int[][] artistIds, double[][] artistWeights, double[] artistNorms) {}

    // How much two weight maps share: how many keys, and their dot product
    private record Overlap(int shared, double dot) {}

    // Every user's taste, for matches(). Loading it reads every counted play (seconds at 500k plays), and matches()
    // runs once per online user on each MATCHES push, so they all share one load. It's reloaded in the background
    // (refresh() before LiveEventService's batched push, and refreshTastes every few minutes for changes nothing
    // announces, like a piece made private), so requests keep using the old tastes instead of waiting for a load.
    // The TTL is only a backstop in case those stop.
    // ponytail: still scores the caller against every user, O(users) per call, just cheaply. Precompute each
    // user's neighbours on refresh (or sample candidates) if that gets slow
    private final CachedValue<Tastes> allTastes = new CachedValue<>(Duration.ofMinutes(15), () -> load(null, null));

    // For the `matches` live event. Leaves out the caller and users they follow. Best first; ties go to more shared
    // artists. Empty when the caller has no plays or favorites. Tastes can be up to one refresh() old.
    public MatchesResponse matches(String userId) {
        Tastes tastes = allTastes.get();
        Taste mine = tastes.byUser().get(userId);
        if (mine == null) {
            return new MatchesResponse(List.of());
        }

        Set<String> skip = new HashSet<>(follows.findFollowingIds(userId));
        skip.add(userId);

        // Ranked on counts and dot products only; the sorted shared lists are built for the top MATCHES_LIMIT
        Index index = tastes.index();
        int me = index.positions().get(userId);
        Comparator<Ranked> best = Comparator.comparingInt(Ranked::compatibility).reversed()
                .thenComparing(Ranked::sharedArtists, Comparator.reverseOrder())
                .thenComparing(Ranked::userId);
        // Worst kept match on top, so it's the one dropped
        PriorityQueue<Ranked> kept = new PriorityQueue<>(best.reversed());
        for (int other = 0; other < index.userIds().length; other++) {
            if (other == me || skip.contains(index.userIds()[other])) continue;
            Overlap tags = tagOverlap(index.tags()[me], index.tags()[other]);
            Overlap artists = artistOverlap(index.artistIds()[me], index.artistWeights()[me],
                    index.artistIds()[other], index.artistWeights()[other]);
            if (tags.shared() == 0 && artists.shared() == 0) continue;
            int compatibility = compatibility(artists, index.artistNorms()[me] * index.artistNorms()[other],
                    tags, index.tagNorms()[me] * index.tagNorms()[other]);
            kept.add(new Ranked(index.userIds()[other], compatibility, artists.shared()));
            if (kept.size() > MATCHES_LIMIT) kept.poll();
        }
        List<Ranked> top = new ArrayList<>(kept);
        top.sort(best);

        Map<String, UserEntity> people = users.findAllById(top.stream().map(Ranked::userId).toList()).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        return new MatchesResponse(top.stream()
                .filter(r -> people.containsKey(r.userId()))
                .map(r -> toMatch(people.get(r.userId()), score(mine, tastes.byUser().get(r.userId())), tastes.artists()))
                .toList());
    }

    private record Ranked(String userId, int compatibility, int sharedArtists) {}

    // Reloads every user's taste now; matches() keeps using the old ones meanwhile
    public void refresh() {
        allTastes.reload();
    }

    // Also runs at startup, so the first visitor doesn't wait for the load
    @Scheduled(initialDelay = 0, fixedRate = 5 * 60_000)
    public void refreshTastes() {
        refresh();
    }

    // For a profile: the viewer's compatibility with its owner. Reads only the two users' plays, so it's cheap
    // and always current. Null when either has no plays or favorites yet.
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
        int compatibility = compatibility(overlap(a.artists(), b.artists()), a.artistNorm() * b.artistNorm(),
                overlap(a.tags(), b.tags()), a.tagNorm() * b.tagNorm());
        return new Score(compatibility, shared(a.artists(), b.artists()), shared(a.tags(), b.tags()));
    }

    // Norms are the product of both users' norms
    private static int compatibility(Overlap artists, double artistNorms, Overlap tags, double tagNorms) {
        double tagCosine = cosine(tags.dot(), tagNorms);
        if (artists.shared() > 0) {
            double raw = ARTIST_SHARE * cosine(artists.dot(), artistNorms) + (1 - ARTIST_SHARE) * tagCosine;
            return (int) Math.round(100 * raw * confidence(artists.shared()));
        }
        if (tags.shared() > 0) {
            return (int) Math.min(TAG_ONLY_CAP, Math.round(100 * tagCosine * confidence(tags.shared())));
        }
        return 0;
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

    // Walks the smaller map
    private static Overlap overlap(Map<String, Double> a, Map<String, Double> b) {
        if (a.size() > b.size()) return overlap(b, a);
        int shared = 0;
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double other = b.get(e.getKey());
            if (other != null) {
                shared++;
                dot += e.getValue() * other;
            }
        }
        return new Overlap(shared, dot);
    }

    private static Overlap tagOverlap(double[] a, double[] b) {
        int shared = 0;
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != 0 && b[i] != 0) {
                shared++;
                dot += a[i] * b[i];
            }
        }
        return new Overlap(shared, dot);
    }

    // Both id arrays are sorted, so one merge pass finds the shared artists
    private static Overlap artistOverlap(int[] idsA, double[] weightsA, int[] idsB, double[] weightsB) {
        int shared = 0;
        double dot = 0;
        for (int i = 0, j = 0; i < idsA.length && j < idsB.length; ) {
            if (idsA[i] == idsB[j]) {
                shared++;
                dot += weightsA[i++] * weightsB[j++];
            } else if (idsA[i] < idsB[j]) {
                i++;
            } else {
                j++;
            }
        }
        return new Overlap(shared, dot);
    }

    private static double cosine(double dot, double norms) {
        return norms == 0 ? 0 : dot / norms;
    }

    private static double norm(Map<String, Double> weights) {
        return Math.sqrt(weights.values().stream().mapToDouble(w -> w * w).sum());
    }

    // userA null loads every user
    private Tastes load(@Nullable String userA, @Nullable String userB) {
        long now = Instant.now().toEpochMilli();
        double halfLife = HALF_LIFE.toMillis();
        Map<String, Map<String, Double>> artistWeights = new HashMap<>();
        Map<String, Map<String, Double>> tagWeights = new HashMap<>();
        Map<String, ArtistSummary> artists = new HashMap<>();

        for (PlayEventRepository.ArtistWeightRow row : playEvents.findArtistWeights(now, halfLife, userA, userB)) {
            artistWeights.computeIfAbsent(row.getUserId(), id -> new HashMap<>()).put(row.getArtistId(), row.getWeight());
            artists.putIfAbsent(row.getArtistId(),
                    new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()));
        }
        for (PlayEventRepository.TagWeightRow row : playEvents.findTagWeights(now, halfLife, userA, userB)) {
            tagWeights.computeIfAbsent(row.getUserId(), id -> new HashMap<>()).put(row.getTag(), row.getWeight());
        }

        Map<String, Taste> byUser = new HashMap<>();
        for (String userId : union(artistWeights.keySet(), tagWeights.keySet())) {
            byUser.put(userId, new Taste(artistWeights.getOrDefault(userId, Map.of()), tagWeights.getOrDefault(userId, Map.of())));
        }
        return new Tastes(byUser, artists, userA == null ? index(byUser) : null);
    }

    private static Index index(Map<String, Taste> byUser) {
        Map<String, Integer> tagIds = new HashMap<>();
        Map<String, Integer> artistIds = new HashMap<>();
        for (Taste taste : byUser.values()) {
            taste.tags().keySet().forEach(tag -> tagIds.putIfAbsent(tag, tagIds.size()));
            taste.artists().keySet().forEach(artist -> artistIds.putIfAbsent(artist, artistIds.size()));
        }

        String[] userIds = byUser.keySet().toArray(String[]::new);
        int n = userIds.length;
        Map<String, Integer> positions = new HashMap<>();
        double[][] tags = new double[n][];
        double[] tagNorms = new double[n];
        int[][] ids = new int[n][];
        double[][] weights = new double[n][];
        double[] artistNorms = new double[n];
        for (int u = 0; u < n; u++) {
            Taste taste = byUser.get(userIds[u]);
            positions.put(userIds[u], u);

            tags[u] = new double[tagIds.size()];
            for (Map.Entry<String, Double> e : taste.tags().entrySet()) tags[u][tagIds.get(e.getKey())] = e.getValue();
            tagNorms[u] = taste.tagNorm();

            List<Map.Entry<String, Double>> sorted = taste.artists().entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> artistIds.get(e.getKey()))).toList();
            ids[u] = sorted.stream().mapToInt(e -> artistIds.get(e.getKey())).toArray();
            weights[u] = sorted.stream().mapToDouble(Map.Entry::getValue).toArray();
            artistNorms[u] = taste.artistNorm();
        }
        return new Index(userIds, positions, tags, tagNorms, ids, weights, artistNorms);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
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
