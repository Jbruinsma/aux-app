package com.aux_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "artists")
public class ArtistEntity {

    @Id
    @Column(name = "artist_id")
    private String artistId;

    // What the API shows instead of the UUID; see PublicId
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    @Column(name = "artist_pfp_url")
    private String artistPfpUrl;

    @Column(name = "artist_banner_url")
    private String artistBannerUrl;

    // Only the creator can change the pfp and banner. Null for artists made before this column existed
    @Column(name = "created_by_user_id")
    private String createdByUserId;

    protected ArtistEntity() {}

    public ArtistEntity(String artistId, String artistName, String createdByUserId) {
        this.artistId = artistId;
        this.publicId = PublicId.generate(PublicId.ARTIST);
        this.artistName = artistName;
        this.createdByUserId = createdByUserId;
    }

    public String getArtistId() { return artistId; }
    public String getPublicId() { return publicId; }
    public String getArtistName() { return artistName; }
    public String getArtistPfpUrl() { return artistPfpUrl; }
    public String getArtistBannerUrl() { return artistBannerUrl; }
    public String getCreatedByUserId() { return createdByUserId; }

    public boolean isEditableBy(String userId) {
        return userId != null && userId.equals(createdByUserId);
    }

    public void setArtistPfpUrl(String artistPfpUrl) {
        this.artistPfpUrl = artistPfpUrl;
    }

    public void setArtistBannerUrl(String artistBannerUrl) {
        this.artistBannerUrl = artistBannerUrl;
    }
}
