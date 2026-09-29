package com.aux_app.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// ponytail: whole-word match only, leetspeak/spacing tricks ("f u c k", "sh1t") get through; normalize input first if that matters.
public final class ProfanityFilter {

    private static final Pattern BANNED = load();

    private ProfanityFilter() {}

    /** Replaces every banned term with one '*' per character. */
    public static String mask(String text) {
        if (text == null) return null;
        return BANNED.matcher(text).replaceAll(m -> "#".repeat(m.group().length()));
    }

    private static Pattern load() {
        try (InputStream in = ProfanityFilter.class.getResourceAsStream("/banned-words.txt")) {
            String terms = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(t -> Arrays.stream(t.split("\\s+")).map(Pattern::quote).collect(Collectors.joining("\\s+")))
                    .collect(Collectors.joining("|"));
            return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + terms + ")(?:e?s)?(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
