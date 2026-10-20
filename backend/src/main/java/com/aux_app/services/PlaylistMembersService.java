package com.aux_app.services;

import com.aux_app.dto.playlist.*;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.NotificationEntity;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.PlaylistMemberEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.NotificationRepository;
import com.aux_app.repository.PlaylistMemberRepository;
import com.aux_app.repository.PlaylistRepository;
import com.aux_app.repository.PlaylistTrackRepository;
import com.aux_app.repository.UserSavedPlaylistRepository;
import com.aux_app.repository.PlaylistRepository.PlaylistWithMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aux_app.services.LiveEventService.Section;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlaylistMembersService extends PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistMembersService.class);

    private final PlaylistMemberRepository members;
    private final EmailService emails;

    public PlaylistMembersService(
            PlaylistRepository playlists,
            UserSavedPlaylistRepository savedPlaylists,
            MusicPieceRepository musicPieces,
            PlaylistTrackRepository tracks,
            UploadService uploads,
            PlaylistMemberRepository members,
            EmailService emails,
            ApplicationEventPublisher events,
            NotificationRepository notifications
    ) {
        super(playlists, savedPlaylists, musicPieces, tracks, uploads, events, notifications);
        this.members = members;
        this.emails = emails;
    }

    public PendingPlaylistInvites retrievePlaylistInvites (String currentUserId) {

        List<PendingPlaylistInvite> pendingInvites = new ArrayList<>();
        List<PlaylistMemberRepository.InviteWithOwner> playlistInvites = members.findInvitesForUser(currentUserId);

        for (PlaylistMemberRepository.InviteWithOwner invite : playlistInvites) {

            PlaylistEntity playlist = invite.playlist();
            PlaylistMemberEntity member = invite.member();
            UserEntity owner = invite.owner();

            if (playlist == null || member == null || owner == null) continue;

            pendingInvites.add(
                    new PendingPlaylistInvite(
                            playlist.getPublicId(),
                            playlist.getPlaylistName(),
                            playlist.getPlaylistCoverUrl(),
                            member.getPermission(),
                            new PlaylistOwner(
                                    owner.getPublicId(),
                                    owner.getProfilePictureUrl(),
                                    owner.getUsername()
                            )
                    )
            );

        }

        return new PendingPlaylistInvites(pendingInvites);
    }

    public PlaylistMemberResponse acceptInvite(String playlistId, UserEntity user) {
        PlaylistMemberEntity member = requireOwnRow(playlistId, user, PlaylistMemberStatus.PENDING,
                "INVITE_NOT_FOUND", "You have no pending invite to this playlist");

        member.accept();
        PlaylistMemberResponse accepted = PlaylistMemberResponse.of(members.save(member), user);

        // The invite is answered, so its notification goes; the owner hears it was accepted
        PlaylistEntity playlist = playlists.findByPublicId(playlistId);
        notifications.deleteInvite(user.getUserId(), member.getPlaylistId());
        notifications.save(NotificationEntity.inviteAccepted(playlist.getOwnerId(), user.getUserId(), member.getPlaylistId()));

        publishTo(user.getUserId(), Section.INVITES, Section.LIBRARY, Section.NOTIFICATIONS);
        publishTo(playlist.getOwnerId(), Section.NOTIFICATIONS);
        return accepted;
    }

    // Declines a PENDING invite.
    public PlaylistMemberRemovalResponse declineInvite(String playlistId, UserEntity user) {
        PlaylistMemberRemovalResponse declined = removeOwnRow(
                playlistId,
                user,
                PlaylistMemberStatus.PENDING,
                "INVITE_NOT_FOUND",
                "You have no pending invite to this playlist"
        );
        publishTo(user.getUserId(), Section.INVITES, Section.NOTIFICATIONS);
        return declined;
    }

    // Leaves a playlist the caller accepted. The owner has no member row, so they can't leave.
    public PlaylistMemberRemovalResponse leave(String playlistId, UserEntity user) {
        PlaylistMemberRemovalResponse left = removeOwnRow(
                playlistId,
                user,
                PlaylistMemberStatus.ACCEPTED,
                "MEMBERSHIP_NOT_FOUND",
                "You are not a member of this playlist"
        );
        publishTo(user.getUserId(), Section.LIBRARY, Section.NOTIFICATIONS);
        return left;
    }

    private PlaylistMemberRemovalResponse removeOwnRow(
            String playlistId,
            UserEntity user,
            PlaylistMemberStatus required,
            String errorCode,
            String message
    ) {
        PlaylistMemberEntity member = requireOwnRow(playlistId, user, required, errorCode, message);

        members.delete(member);
        // They lose the playlist, so its notifications go too (the invite one, when declining)
        notifications.deleteForPlaylist(user.getUserId(), member.getPlaylistId());

        return new PlaylistMemberRemovalResponse(
                new PlaylistOwner(
                        user.getPublicId(),
                        user.getProfilePictureUrl(),
                        user.getUsername()
                ),
                member.getStatus()
        );
    }

    // 404s unless the caller has a member row on the playlist with the required status.
    private PlaylistMemberEntity requireOwnRow(
            String playlistId,
            UserEntity user,
            PlaylistMemberStatus required,
            String errorCode,
            String message
    ) {
        PlaylistRepository.PlaylistWithCaller found = playlists.findPlaylistWithCaller(playlistId, user.getUserId());

        if (found == null || found.member() == null || found.member().getStatus() != required) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    errorCode,
                    message,
                    "playlistId"
            );
        }
        return found.member();
    }

    public PlaylistMemberResponse invite(String playlistId, UserEntity owner, PlaylistInvitationDetails details) {
        PlaylistWithMember found = findOwned(playlistId, owner, details.userId());
        UserEntity invitee = found.invitee();

        if (invitee.getUserId().equals(owner.getUserId())) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FIELD",
                    "You cannot invite yourself",
                    "userId"
            );
        }

        if (found.member() != null) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "USER_ALREADY_INVITED",
                    "This user has already been invited",
                    "userId"
            );
        }

        PlaylistMemberEntity member = members.save(
                new PlaylistMemberEntity(
                        found.playlist().getPlaylistId(),
                        invitee.getUserId(),
                        details.permission()
                )
        );
        notifications.save(NotificationEntity.playlistInvite(
                invitee.getUserId(), owner.getUserId(), member.getPlaylistId(), member.getPermission()));
        publishTo(invitee.getUserId(), Section.INVITES, Section.NOTIFICATIONS);

        // The invite row is saved, so a mail failure must not fail the request.
        try {
            emails.sendPlaylistInvitation(
                    invitee.getEmail(),
                    found.playlist().getPlaylistName(),
                    owner.getUsername()
            );
        } catch (Exception e) {
            log.warn("Could not send playlist invitation email for playlist {}", playlistId, e);
        }

        return PlaylistMemberResponse.of(member, invitee);
    }

    public PlaylistMemberResponse updatePermission(
            String playlistId,
            UserEntity owner,
            PlaylistInvitationDetails details
    ) {
        PlaylistWithMember found = findOwned(playlistId, owner, details.userId());
        PlaylistMemberEntity member = found.member();

        if (member == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_MEMBER",
                    "User is not a member of this playlist",
                    "userId"
            );
        }

        member.setPermission(details.permission());
        PlaylistMemberResponse updated = PlaylistMemberResponse.of(members.save(member), found.invitee());
        // A pending invite shows its permission, in the invites list and in its notification
        notifications.updateInvitePermission(found.invitee().getUserId(), member.getPlaylistId(), member.getPermission());
        publishTo(found.invitee().getUserId(), Section.INVITES, Section.NOTIFICATIONS);
        return updated;
    }

    public PlaylistMemberRemovalResponse remove(
            String playlistId,
            UserEntity owner,
            PlaylistMemberRemoval details
    ) {
        PlaylistWithMember found = findOwned(playlistId, owner, details.userId());
        PlaylistMemberEntity member = found.member();

        if (member == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "USER_NOT_MEMBER",
                    "User is not a member of this playlist", "userId");
        }

        members.delete(member);
        UserEntity user = found.invitee();
        // A removed invite leaves their bell, a removed member loses it from their library. Either way they
        // lose the playlist, so its notifications go too
        notifications.deleteForPlaylist(user.getUserId(), member.getPlaylistId());
        publishTo(user.getUserId(), Section.INVITES, Section.LIBRARY, Section.NOTIFICATIONS);
        return new PlaylistMemberRemovalResponse(
                new PlaylistOwner(
                        user.getPublicId(),
                        user.getProfilePictureUrl(),
                        user.getUsername()
                ),
                member.getStatus()
        );
    }

    public List<PlaylistMemberResponse> list(String playlistId, UserEntity owner) {
        PlaylistEntity playlist = findOwnedPlaylist(playlistId, owner.getUserId());

        return members.findMembersWithUsers(playlist.getPlaylistId()).stream()
                .map(row -> PlaylistMemberResponse.of(row.member(), row.user()))
                .toList();
    }

    // 404s if the playlist is missing or not owned by `owner` (indistinguishable on purpose), or the target user is unknown.
    private PlaylistWithMember findOwned(String playlistId, UserEntity owner, String targetPublicId) {
        PlaylistWithMember found = playlists.findPlaylistWithMember(playlistId, targetPublicId);

        if (found == null || !found.playlist().getOwnerId().equals(owner.getUserId())) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        if (found.invitee() == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found",
                    "userId"
            );
        }

        return found;
    }
}
