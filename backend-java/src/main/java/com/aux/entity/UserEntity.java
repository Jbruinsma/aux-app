package com.aux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Example entity: maps the existing `users` table (see backend/models.py UserModel).
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Column(name = "password_hash")
    private byte[] passwordHash;

    // JSON string, same as Python's json.dumps(user.last_playback)
    @Column(name = "last_playback")
    private String lastPlayback;

    protected UserEntity() {}

    // New user, same defaults as backend/classes/user.py User.__init__
    public UserEntity(String id, String username, byte[] passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.profilePicture = "/default_profile_picture.svg";
        this.lastPlayback = "{\"repeatOn\": false, \"shuffleOn\": false, \"playlistUUID\": null, "
                + "\"musicPieceUUID\": null, \"position\": 0, \"current_playlist_index\": null}";
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getProfilePicture() { return profilePicture; }
    public byte[] getPasswordHash() { return passwordHash; }
    public String getLastPlayback() { return lastPlayback; }
}
