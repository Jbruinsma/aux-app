package com.aux_app.entity;

import com.aux_app.dto.users.RepeatPreference;
import jakarta.persistence.*;

@Entity
@Table(name = "user_play_preferences")
public class UserPlayPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "shuffle_enabled", nullable = false)
    private boolean shuffleEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_preference", nullable = false)
    private RepeatPreference repeatPreference = RepeatPreference.OFF;

    protected UserPlayPreferencesEntity() {}

    public UserPlayPreferencesEntity(String userId) {
        this.userId = userId;
    }

    public boolean isShuffleEnabled() {
        return this.shuffleEnabled;
    }

    public RepeatPreference getRepeatPreference() {
        return this.repeatPreference;
    }

    public void setShuffleEnabled(boolean shuffleEnabled) {
        this.shuffleEnabled = shuffleEnabled;
    }

    public void setRepeatPreference(RepeatPreference repeatPreference) {
        this.repeatPreference = repeatPreference;
    }
}
