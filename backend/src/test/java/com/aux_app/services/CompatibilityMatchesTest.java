package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aux_app.dto.users.CompatibilityMatch;
import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.PlayEventRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.CompatibilityService.Score;
import com.aux_app.services.CompatibilityService.Taste;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.StreamSupport;

// matches() ranks on arrays for speed; it must pick and order exactly what scoring every pair with score() would
class CompatibilityMatchesTest {

    record ArtistRow(String getUserId, String getArtistId, String getArtistName, String getArtistPfpUrl, Double getWeight)
            implements PlayEventRepository.ArtistWeightRow {}

    record TagRow(String getUserId, String getTag, Double getWeight) implements PlayEventRepository.TagWeightRow {}

    @Test
    void sameTopMatchesAsScoringEveryPair() {
        Random random = new Random(42);
        List<PlayEventRepository.ArtistWeightRow> artistRows = new ArrayList<>();
        List<PlayEventRepository.TagWeightRow> tagRows = new ArrayList<>();
        Map<String, Map<String, Double>> artists = new HashMap<>();
        Map<String, Map<String, Double>> tags = new HashMap<>();
        for (int u = 0; u < 300; u++) {
            String user = "u" + u;
            // A few users with only artists or only tags, and weights from rounding so scores tie
            for (int i = 0, n = u % 7 == 0 ? 0 : random.nextInt(6); i < n; i++) {
                String artist = "a" + random.nextInt(40);
                double weight = Math.round(random.nextDouble() * 4) / 2.0 + 0.5;
                if (artists.computeIfAbsent(user, k -> new HashMap<>()).putIfAbsent(artist, weight) == null) {
                    artistRows.add(new ArtistRow(user, artist, artist, null, weight));
                }
            }
            for (int i = 0, n = u % 11 == 0 ? 0 : random.nextInt(5); i < n; i++) {
                String tag = "t" + random.nextInt(10);
                double weight = Math.round(random.nextDouble() * 4) / 2.0 + 0.5;
                if (tags.computeIfAbsent(user, k -> new HashMap<>()).putIfAbsent(tag, weight) == null) {
                    tagRows.add(new TagRow(user, tag, weight));
                }
            }
        }

        PlayEventRepository plays = mock(PlayEventRepository.class);
        when(plays.findArtistWeights(anyLong(), anyDouble(), any(), any())).thenReturn(artistRows);
        when(plays.findTagWeights(anyLong(), anyDouble(), any(), any())).thenReturn(tagRows);
        UserRepository users = mock(UserRepository.class);
        when(users.findAllById(any())).thenAnswer(call -> StreamSupport
                .stream(((Iterable<String>) call.getArgument(0)).spliterator(), false)
                .map(id -> new UserEntity(id, id + "@x", "x", OnboardingStep.DONE)).toList());
        FollowRepository follows = mock(FollowRepository.class);
        when(follows.findFollowingIds(any())).thenReturn(List.of("u3", "u5"));
        CompatibilityService service = new CompatibilityService(plays, users, follows);

        for (String me : List.of("u1", "u2", "u14", "u22")) {
            Taste mine = taste(artists, tags, me);
            record Expected(String user, Score score) {}
            List<Expected> all = new ArrayList<>();
            for (String other : union(artists, tags)) {
                if (other.equals(me) || other.equals("u3") || other.equals("u5")) continue;
                Score score = CompatibilityService.score(mine, taste(artists, tags, other));
                if (score.sharesAnything()) all.add(new Expected(other, score));
            }
            all.sort(Comparator.comparingInt((Expected e) -> e.score().compatibility()).reversed()
                    .thenComparing(e -> e.score().sharedArtists().size(), Comparator.reverseOrder())
                    .thenComparing(Expected::user));

            List<String> expected = all.stream().limit(CompatibilityService.MATCHES_LIMIT)
                    .map(e -> new UserEntity(e.user(), "", "", OnboardingStep.DONE).getUsername() + ":" + e.score().compatibility())
                    .toList();
            List<String> actual = service.matches(me).matches().stream()
                    .map((CompatibilityMatch m) -> m.user().username() + ":" + m.compatibility()).toList();
            assertEquals(CompatibilityService.MATCHES_LIMIT, expected.size(), "test data too sparse for " + me);
            assertEquals(expected, actual, me);
        }
    }

    private static Taste taste(Map<String, Map<String, Double>> artists, Map<String, Map<String, Double>> tags, String user) {
        return new Taste(artists.getOrDefault(user, Map.of()), tags.getOrDefault(user, Map.of()));
    }

    private static List<String> union(Map<String, ?> a, Map<String, ?> b) {
        List<String> all = new ArrayList<>(a.keySet());
        b.keySet().stream().filter(k -> !a.containsKey(k)).forEach(all::add);
        return all;
    }
}
