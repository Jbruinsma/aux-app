package com.aux_app.entity;

import com.aux_app.dto.base.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profile_details")
public class ProfileDetailsEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "display_name", nullable = true)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "country", nullable = true)
    private Country country;

    @Column(name = "website", nullable = true)
    private String website;

    @Column(name = "about", nullable = true, length = 200)
    private String about;

    protected ProfileDetailsEntity() {}

    public ProfileDetailsEntity(String userId) {
        this.userId = userId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public void setAbout(String about) {
        this.about = about;
    }
}
