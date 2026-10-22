package com.aux_app.services;

import com.aux_app.error.AuxException;
import com.aux_app.repository.TagRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TagService {

    public static final int MAX_TAGS = 5;
    private static final int SUGGESTION_LIMIT = 20;
    // 2-24 chars after normalizing, at least one letter or digit. No commas: TagRepository joins tags with them
    private static final Pattern VALID = Pattern.compile("(?=.*[a-z0-9])[a-z0-9 &-]{2,24}");

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    // Null stays null ("leave tags unchanged"). Otherwise lowercased, trimmed, inner whitespace collapsed,
    // blanks dropped and duplicates removed, so a lone blank value means "no tags".
    public static @Nullable List<String> normalize(@Nullable List<String> raw) {
        if (raw == null) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String tag : raw) {
            String clean = tag == null ? "" : tag.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (clean.isEmpty()) {
                continue;
            }
            if (!VALID.matcher(clean).matches()) {
                throw invalid("Tags are 2-24 letters, digits, spaces, '&' or '-'");
            }
            if (ProfanityFilter.contains(clean)) {
                throw invalid("Tag not allowed: " + clean);
            }
            result.add(clean);
        }
        if (result.size() > MAX_TAGS) {
            throw invalid("At most " + MAX_TAGS + " tags");
        }
        return List.copyOf(result);
    }

    // Replace a piece's tags. `musicPieceId` is the internal id; `normalized` comes from normalize()
    @Transactional
    public void setPieceTags(String musicPieceId, List<String> normalized) {
        tags.clearPieceTags(musicPieceId);
        if (!normalized.isEmpty()) {
            createMissing(normalized);
            tags.addPieceTags(musicPieceId, normalized);
        }
    }

    // Replace a playlist's tags. `playlistId` is the internal id; `normalized` comes from normalize()
    @Transactional
    public void setPlaylistTags(String playlistId, List<String> normalized) {
        tags.clearPlaylistTags(playlistId);
        if (!normalized.isEmpty()) {
            createMissing(normalized);
            tags.addPlaylistTags(playlistId, normalized);
        }
    }

    public List<String> suggest(@Nullable String query) {
        String text = query == null ? "" : query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String prefix = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return tags.suggest(prefix, SUGGESTION_LIMIT);
    }

    private void createMissing(List<String> normalized) {
        for (String tag : normalized) {
            tags.insertIfMissing(UUID.randomUUID().toString(), tag);
        }
    }

    private static AuxException invalid(String message) {
        return new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", message, "tags");
    }
}
