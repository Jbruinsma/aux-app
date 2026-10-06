package com.aux_app.entity;

import java.security.SecureRandom;

// Short id the API shows instead of the internal UUID, e.g. "p_7c2dK1". The UUID stays the primary key and FK target.
// 6 base62 chars = ~56.8B values per type; a collision hits the unique index (409) instead of corrupting data.
public final class PublicId {

    public static final String USER = "u_";
    public static final String PLAYLIST = "p_";
    public static final String ARTIST = "a_";
    public static final String MUSIC_PIECE = "m_";

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicId() {}

    public static String generate(String prefix) {
        StringBuilder id = new StringBuilder(prefix);
        for (int i = 0; i < LENGTH; i++) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
