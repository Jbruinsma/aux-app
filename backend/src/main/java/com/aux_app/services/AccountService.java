package com.aux_app.services;

import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// Deletes an account and everything the user had
@Service
public class AccountService {

    private static final String PIECES = "(SELECT music_piece_id FROM music_pieces WHERE uploader_user_id = :userId)";
    private static final String PLAYLISTS = "(SELECT playlist_id FROM playlists WHERE owner_id = :userId)";

    // In foreign-key order (foreign_keys is on). playlist_members, notifications, otp_codes and user_play_preferences
    // cascade on their own. The user's pieces leave every playlist they were in, other people's plays and favorites
    // of them go too, and other people's plays from the user's playlists keep the play and lose the playlist.
    // Artists the user created stay (other people's pieces may use them); only the creator link goes.
    static final List<String> DELETES = List.of(
            "DELETE FROM playlist_tracks WHERE music_piece_id IN " + PIECES + " OR playlist_id IN " + PLAYLISTS,
            "DELETE FROM user_saved_playlists WHERE user_id = :userId OR playlist_id IN " + PLAYLISTS,
            "DELETE FROM playlist_tags WHERE playlist_id IN " + PLAYLISTS,
            "DELETE FROM music_piece_tags WHERE music_piece_id IN " + PIECES,
            "DELETE FROM user_favorite_tracks WHERE user_id = :userId OR music_piece_id IN " + PIECES,
            "DELETE FROM user_favorite_artists WHERE user_id = :userId",
            "DELETE FROM play_events WHERE user_id = :userId OR music_piece_id IN " + PIECES,
            "UPDATE play_events SET context_playlist_id = NULL WHERE context_playlist_id IN " + PLAYLISTS,
            "DELETE FROM follows WHERE follower_user_id = :userId OR following_user_id = :userId",
            "UPDATE artists SET created_by_user_id = NULL WHERE created_by_user_id = :userId",
            "DELETE FROM music_pieces WHERE uploader_user_id = :userId",
            "DELETE FROM playlists WHERE owner_id = :userId",
            "DELETE FROM profile_details WHERE user_id = :userId",
            "DELETE FROM users WHERE user_id = :userId"
    );

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final EntityManager em;
    private final UploadService uploads;
    private final ApplicationEventPublisher events;

    public AccountService(EntityManager em, UploadService uploads, ApplicationEventPublisher events) {
        this.em = em;
        this.uploads = uploads;
        this.events = events;
    }

    @Transactional
    public void deleteAccount(UserEntity user, String currentPassword) {
        // 403, not 401: the frontend treats any 401 as an expired session and logs out
        if (!bcrypt.matches(currentPassword, user.getPasswordHash())) {
            throw new AuxException(HttpStatus.FORBIDDEN, "WRONG_PASSWORD", "Current password is wrong", "currentPassword");
        }
        String userId = user.getUserId();

        // Read before the rows go
        List<String> tracks = strings("SELECT mp3_file_url FROM music_pieces WHERE uploader_user_id = :userId", userId);
        List<String> images = new ArrayList<>(strings("""
                SELECT cover_url FROM music_pieces WHERE uploader_user_id = :userId AND cover_url IS NOT NULL
                UNION ALL SELECT playlist_cover_url FROM playlists WHERE owner_id = :userId AND playlist_cover_url IS NOT NULL
                """, userId));
        images.add(user.getProfilePictureUrl());
        images.add(user.getBannerUrl());

        for (String sql : DELETES) {
            em.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
        }

        // ponytail: refreshes every section for everyone online; a deletion touches playlists, recents, top artists
        // and matches all over. Scope it to the affected users if deletions ever get common
        events.publishEvent(LiveEventService.Update.everyoneOnline(EnumSet.allOf(LiveEventService.Section.class)));

        // After commit, so a rollback never leaves live rows pointing at deleted files. If a delete fails, the file
        // is just orphaned in R2.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tracks.forEach(uploads::deleteTrack);
                images.forEach(uploads::deleteImage);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(String sql, String userId) {
        return em.createNativeQuery(sql).setParameter("userId", userId).getResultList();
    }
}
