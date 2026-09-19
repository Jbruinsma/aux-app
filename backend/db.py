import json

from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

from backend.config import DB_PATH
from backend.classes.music_piece import MusicPiece
from backend.classes.playlist import Playlist
from backend.classes.user import User
from backend.models import (
    Base,
    MusicPieceModel,
    PlaylistModel,
    PlaylistSavedBy,
    PlaylistSharedWith,
    UserModel,
    UserPlaylistAddedTo,
    UserSavedPlaylist,
)

engine = create_engine(f"sqlite:///{DB_PATH}")


@event.listens_for(engine, "connect")
def _enable_foreign_keys(dbapi_connection, _):
    dbapi_connection.execute("PRAGMA foreign_keys=ON")


def init_db() -> None:
    Base.metadata.create_all(engine)


def load_all_users() -> dict[str, User]:
    users: dict[str, User] = {}
    playlists: dict[str, Playlist] = {}

    id_to_username: dict[str, str] = {}

    with Session(engine) as session:
        for row in session.query(UserModel).all():
            user = User.__new__(User)
            user.uuid = row.id
            user.username = row.username
            user.profile_picture = row.profile_picture
            user.password_hash = row.password_hash
            user.playlists = {}
            user.playlists_added_to = {}
            user.saved_playlists = {}
            user.last_playback = json.loads(row.last_playback)
            users[user.username] = user
            id_to_username[row.id] = row.username

        for row in session.query(PlaylistModel).all():
            owner_username = id_to_username.get(row.owner_id)
            playlist = Playlist(
                uuid=row.uuid,
                owner=owner_username,
                name=row.name,
                is_public=bool(row.is_public),
                playlist_cover=row.cover,
            )
            playlists[playlist.UUID] = playlist
            owner = users.get(owner_username)
            if owner is not None:
                owner.playlists[playlist.UUID] = playlist

        for row in session.query(MusicPieceModel).order_by(MusicPieceModel.playlist_uuid, MusicPieceModel.position):
            playlist = playlists.get(row.playlist_uuid)
            if playlist is None:
                continue
            piece = MusicPiece.__new__(MusicPiece)
            piece.UUID = row.uuid
            piece.mp3_file = row.mp3_file
            piece.cover = row.cover
            piece.name = row.name
            piece.artist = row.artist
            piece.is_favorite = bool(row.is_favorite)
            playlist.music_pieces.append(piece)

        for row in session.query(PlaylistSharedWith).order_by(PlaylistSharedWith.playlist_uuid, PlaylistSharedWith.position):
            playlist = playlists.get(row.playlist_uuid)
            shared_username = id_to_username.get(row.user_id)
            if playlist is not None and shared_username is not None:
                playlist.shared_with.append({"username": shared_username, "canEdit": bool(row.can_edit)})

        for row in session.query(PlaylistSavedBy).order_by(PlaylistSavedBy.playlist_uuid, PlaylistSavedBy.position):
            playlist = playlists.get(row.playlist_uuid)
            saved_username = id_to_username.get(row.user_id)
            if playlist is not None and saved_username is not None:
                playlist.saved_by.append(saved_username)

        for row in session.query(UserPlaylistAddedTo).all():
            user = users.get(id_to_username.get(row.user_id))
            if user is not None:
                owner_username = id_to_username.get(row.owner_id)
                user.playlists_added_to[row.playlist_uuid] = {
                    "owner": owner_username,
                    "playlistUUID": row.playlist_uuid,
                }

        for row in session.query(UserSavedPlaylist).all():
            user = users.get(id_to_username.get(row.user_id))
            if user is not None:
                owner_username = id_to_username.get(row.owner_id)
                user.saved_playlists[row.playlist_uuid] = {
                    "owner": owner_username,
                    "playlistUUID": row.playlist_uuid,
                }

    return users


def save_all_users(users: dict[str, User]) -> None:
    username_to_id = {user.username: user.uuid for user in users.values()}

    with Session(engine) as session:
        session.query(MusicPieceModel).delete()
        session.query(PlaylistSharedWith).delete()
        session.query(PlaylistSavedBy).delete()
        session.query(UserPlaylistAddedTo).delete()
        session.query(UserSavedPlaylist).delete()
        session.query(PlaylistModel).delete()
        session.query(UserModel).delete()

        for user in users.values():
            session.add(
                UserModel(
                    id=user.uuid,
                    username=user.username,
                    profile_picture=user.profile_picture,
                    password_hash=user.password_hash,
                    last_playback=json.dumps(user.last_playback),
                )
            )

            for playlist in user.playlists.values():
                session.add(
                    PlaylistModel(
                        uuid=playlist.UUID,
                        owner_id=username_to_id.get(playlist.owner),
                        name=playlist.name,
                        cover=playlist.playlist_cover,
                        is_public=int(playlist.is_public),
                    )
                )

                for position, piece in enumerate(playlist.music_pieces):
                    session.add(
                        MusicPieceModel(
                            uuid=piece.UUID,
                            playlist_uuid=playlist.UUID,
                            position=position,
                            mp3_file=piece.mp3_file,
                            cover=piece.cover,
                            name=piece.name,
                            artist=piece.artist,
                            is_favorite=int(piece.is_favorite),
                        )
                    )

                for position, shared in enumerate(playlist.shared_with):
                    shared_user_id = username_to_id.get(shared["username"])
                    if shared_user_id is None:
                        continue
                    session.add(
                        PlaylistSharedWith(
                            playlist_uuid=playlist.UUID,
                            user_id=shared_user_id,
                            can_edit=int(shared["canEdit"]),
                            position=position,
                        )
                    )

                for position, saved_username in enumerate(playlist.saved_by):
                    saved_user_id = username_to_id.get(saved_username)
                    if saved_user_id is None:
                        continue
                    session.add(
                        PlaylistSavedBy(
                            playlist_uuid=playlist.UUID,
                            user_id=saved_user_id,
                            position=position,
                        )
                    )

            for entry in user.playlists_added_to.values():
                session.add(
                    UserPlaylistAddedTo(
                        user_id=user.uuid,
                        playlist_uuid=entry["playlistUUID"],
                        owner_id=username_to_id.get(entry["owner"]),
                    )
                )

            for entry in user.saved_playlists.values():
                session.add(
                    UserSavedPlaylist(
                        user_id=user.uuid,
                        playlist_uuid=entry["playlistUUID"],
                        owner_id=username_to_id.get(entry["owner"]),
                    )
                )

        session.commit()
