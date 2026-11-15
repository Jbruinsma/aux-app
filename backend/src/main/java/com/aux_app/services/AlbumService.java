package com.aux_app.services;

import com.aux_app.dto.album.AlbumCreationDetails;
import com.aux_app.dto.album.AlbumDetails;
import com.aux_app.dto.album.AlbumDetailsUpdate;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.AlbumEntity;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.entity.UserEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Albums and the covers they share with their pieces. A piece that uses its album's cover holds a copy of the
// album's cover_url, so the album's cover object may be shown by pieces in and out of the album: covers are only
// deleted through UploadService.deleteImageIfUnused
@Service
public class AlbumService {

    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final UserRepository users;
    private final UploadService uploads;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public AlbumService(
            AlbumRepository albums,
            ArtistRepository artists,
            UserRepository users,
            UploadService uploads,
            TransactionTemplate transactions,
            ApplicationEventPublisher events
    ) {
        this.albums = albums;
        this.artists = artists;
        this.users = users;
        this.uploads = uploads;
        this.transactions = transactions;
        this.events = events;
    }

    // Anyone signed in can add an album to any artist. An album with the same name (case-insensitive) is returned
    // as is, so the cover is only needed, and only read, when the album is new
    public AlbumEntity findOrCreate(UserEntity user, ArtistEntity artist, AlbumCreationDetails details) {
        AlbumEntity existing = albums.findByArtistIdAndAlbumNameIgnoreCase(artist.getArtistId(), details.albumName());
        if (existing != null) {
            return existing;
        }
        if (details.coverImage() == null) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "A new album needs a cover", "coverImage");
        }
        AlbumEntity album = new AlbumEntity(UUID.randomUUID().toString(), artist.getArtistId(), details.albumName(),
                user.getUserId());
        // A racing create hits uq_albums_artist_name_nocase and AuxErrorHandler returns 409; store removes the image
        uploads.storeAlbumCover(album, details.coverImage(), () -> albums.save(album));
        return album;
    }

    public AlbumEntity update(UserEntity user, String albumId, AlbumDetailsUpdate details) {
        AlbumEntity album = findEditableAlbum(albumId, user);

        if (details.albumName() != null) {
            AlbumEntity sameName = albums.findByArtistIdAndAlbumNameIgnoreCase(album.getArtistId(), details.albumName());
            if (sameName != null && !sameName.getAlbumId().equals(album.getAlbumId())) {
                throw new AuxException(HttpStatus.CONFLICT, "ALBUM_NAME_TAKEN",
                        "The artist already has an album with that name", "albumName");
            }
            album.setAlbumName(details.albumName());
        }

        if (details.coverImage() != null) {
            String oldCover = album.getCoverUrl();
            // One transaction, so no piece ever points at a cover the album no longer has
            uploads.storeAlbumCover(album, details.coverImage(), () -> transactions.executeWithoutResult(status -> {
                albums.save(album);
                albums.syncPieceCovers(album.getAlbumId(), album.getCoverUrl());
            }));
            // Pieces that left the album with this cover still show it
            uploads.deleteImageIfUnused(oldCover);
        } else {
            albums.save(album);
        }

        publishUploads(album);
        return album;
    }

    // Pieces stay, as singles, and keep the cover they show
    public void delete(UserEntity user, String albumId) {
        AlbumEntity album = findEditableAlbum(albumId, user);
        Set<String> uploaders = new HashSet<>(albums.findUploaderIds(album.getAlbumId()));

        transactions.executeWithoutResult(status -> {
            albums.detachPieces(album.getAlbumId());
            albums.delete(album);
        });
        uploads.deleteImageIfUnused(album.getCoverUrl());

        publish(uploaders);
    }

    // An album to put a piece of `artistId` (internal id) in
    public AlbumEntity albumForPiece(String albumId, String artistId) {
        AlbumEntity album = findAlbum(albumId);
        if (!album.getArtistId().equals(artistId)) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "ALBUM_ARTIST_MISMATCH",
                    "The album belongs to a different artist", "albumId");
        }
        return album;
    }

    // Applies a piece edit's album fields, after any artist change. `albumId` (public), `removeFromAlbum` and
    // `useAlbumCover` are the edit's values, null when not sent; `uploadingCover` is whether a new cover comes with
    // the edit (UploadService.replaceMusicPieceCover then handles the cover). Returns the cover the piece stopped
    // showing, for deleteImageIfUnused once the piece is saved, or null.
    // A piece that leaves its album, or moves to another artist without a new album, keeps the cover it shows
    public String placePiece(MusicPieceEntity piece, String albumId, Boolean removeFromAlbum, Boolean useAlbumCover,
                             boolean uploadingCover) {
        boolean remove = Boolean.TRUE.equals(removeFromAlbum);
        if (albumId != null && remove) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD",
                    "Send albumId or removeFromAlbum, not both", "removeFromAlbum");
        }
        if (uploadingCover && Boolean.TRUE.equals(useAlbumCover)) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD",
                    "Send coverImage or useAlbumCover, not both", "useAlbumCover");
        }

        AlbumEntity album;
        if (remove) {
            album = null;
        } else if (albumId != null) {
            album = albumForPiece(albumId, piece.getArtistId());
        } else if (piece.getAlbumId() != null) {
            AlbumEntity current = albums.findById(piece.getAlbumId()).orElse(null);
            album = current != null && current.getArtistId().equals(piece.getArtistId()) ? current : null;
        } else {
            album = null;
        }

        if (album == null && Boolean.TRUE.equals(useAlbumCover)) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "NOT_IN_ALBUM",
                    "Only a piece in an album can use the album's cover", "useAlbumCover");
        }
        piece.setAlbumId(album == null ? null : album.getAlbumId());

        if (uploadingCover) {
            return null;
        }
        boolean follow = album != null
                && (Boolean.TRUE.equals(useAlbumCover) || (piece.usesAlbumCover() && useAlbumCover == null));
        if (!follow) {
            piece.setUsesAlbumCover(false);
            return null;
        }
        String dropped = piece.getCoverUrl();
        piece.setCoverUrl(album.getCoverUrl());
        piece.setUsesAlbumCover(true);
        return album.getCoverUrl().equals(dropped) ? null : dropped;
    }

    public AlbumEntity findAlbum(String albumId) {
        AlbumEntity album = albums.findByPublicId(albumId);
        if (album == null) {
            throw notFound();
        }
        return album;
    }

    // An album with no piece the caller can play is only shown to its editors, as on the artist page
    public AlbumEntity findVisibleAlbum(String albumId, String userId) {
        AlbumEntity album = findAlbum(albumId);
        if (albums.countPlayablePieces(album.getAlbumId(), userId) == 0 && !album.isEditableBy(userId, artistOf(album))) {
            throw notFound();
        }
        return album;
    }

    public AlbumDetails details(AlbumEntity album, String userId) {
        ArtistEntity artist = artistOf(album);
        PlaylistOwner createdBy = album.getCreatedByUserId() == null ? null : users.findById(album.getCreatedByUserId())
                .map(u -> new PlaylistOwner(u.getPublicId(), u.getProfilePictureUrl(), u.getUsername()))
                .orElse(null);
        return new AlbumDetails(
                album.getPublicId(),
                album.getAlbumName(),
                album.getCoverUrl(),
                new ArtistSummary(artist.getPublicId(), artist.getArtistName(), artist.getArtistPfpUrl()),
                albums.countPlayablePieces(album.getAlbumId(), userId),
                album.isEditableBy(userId, artist),
                createdBy,
                album.getCreatedAt());
    }

    private AlbumEntity findEditableAlbum(String albumId, UserEntity user) {
        AlbumEntity album = findAlbum(albumId);
        if (!album.isEditableBy(user.getUserId(), artistOf(album))) {
            throw new AuxException(HttpStatus.FORBIDDEN, "NOT_ALBUM_EDITOR",
                    "Only the album's creator or the artist's creator can change it", "albumId");
        }
        return album;
    }

    private ArtistEntity artistOf(AlbumEntity album) {
        return artists.findById(album.getArtistId()).orElseThrow();
    }

    private static AuxException notFound() {
        return new AuxException(HttpStatus.NOT_FOUND, "ALBUM_NOT_FOUND", "Album not found", "albumId");
    }

    // ponytail: only uploaders with pieces in the album refresh live; other listeners catch up on their next refresh
    private void publishUploads(AlbumEntity album) {
        publish(new HashSet<>(albums.findUploaderIds(album.getAlbumId())));
    }

    private void publish(Set<String> uploaders) {
        if (!uploaders.isEmpty()) {
            events.publishEvent(new LiveEventService.Update(uploaders,
                    Set.of(LiveEventService.Section.UPLOADS, LiveEventService.Section.RECENTS)));
        }
    }
}
