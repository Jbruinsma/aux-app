package com.aux.entity;

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

    @Column(name = "artist_name", nullable = false)
    private String artistName;

    @Column(name = "artist_pfp_url")
    private String artistPfpUrl;

    protected ArtistEntity() {}

    public ArtistEntity(String artistId, String artistName) {
        this.artistId = artistId;
        this.artistName = artistName;
    }

    public String getArtistId() { return artistId; }
    public String getArtistName() { return artistName; }
    public String getArtistPfpUrl() { return artistPfpUrl; }
}
