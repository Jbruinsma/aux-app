import json
import sqlite3

from backend.config import DB_PATH
from backend.classes.music_piece import MusicPiece
from backend.classes.playlist import Playlist
from backend.classes.user import User

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    profile_picture TEXT,
    password_hash BLOB,
    last_playback TEXT
);
CREATE TABLE IF NOT EXISTS playlists (
    uuid TEXT PRIMARY KEY,
    owner TEXT,
    name TEXT,
    cover TEXT,
    is_public INTEGER
);
CREATE TABLE IF NOT EXISTS music_pieces (
    uuid TEXT PRIMARY KEY,
    playlist_uuid TEXT,
    position INTEGER,
    mp3_file TEXT,
    cover TEXT,
    name TEXT,
    artist TEXT,
    is_favorite INTEGER
);
CREATE TABLE IF NOT EXISTS playlist_shared_with (
    playlist_uuid TEXT,
    username TEXT,
    can_edit INTEGER,
    position INTEGER
);
CREATE TABLE IF NOT EXISTS playlist_saved_by (
    playlist_uuid TEXT,
    username TEXT,
    position INTEGER
);
CREATE TABLE IF NOT EXISTS user_playlists_added_to (
    username TEXT,
    playlist_uuid TEXT,
    owner TEXT,
    PRIMARY KEY (username, playlist_uuid)
);
CREATE TABLE IF NOT EXISTS user_saved_playlists (
    username TEXT,
    playlist_uuid TEXT,
    owner TEXT,
    PRIMARY KEY (username, playlist_uuid)
);
"""


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_connection()
    conn.executescript(SCHEMA)
    conn.commit()
    conn.close()


def load_all_users() -> dict[str, User]:
    conn = get_connection()

    users: dict[str, User] = {}
    for row in conn.execute("SELECT * FROM users"):
        user = User.__new__(User)
        user.username = row["username"]
        user.profile_picture = row["profile_picture"]
        user.password_hash = row["password_hash"]
        user.playlists = {}
        user.playlists_added_to = {}
        user.saved_playlists = {}
        user.last_playback = json.loads(row["last_playback"])
        users[user.username] = user

    playlists: dict[str, Playlist] = {}
    for row in conn.execute("SELECT * FROM playlists"):
        playlist = Playlist(
            uuid=row["uuid"],
            owner=row["owner"],
            name=row["name"],
            is_public=bool(row["is_public"]),
            playlist_cover=row["cover"],
        )
        playlists[playlist.UUID] = playlist
        owner = users.get(playlist.owner)
        if owner is not None:
            owner.playlists[playlist.UUID] = playlist

    for row in conn.execute("SELECT * FROM music_pieces ORDER BY playlist_uuid, position"):
        playlist = playlists.get(row["playlist_uuid"])
        if playlist is None:
            continue
        piece = MusicPiece.__new__(MusicPiece)
        piece.UUID = row["uuid"]
        piece.mp3_file = row["mp3_file"]
        piece.cover = row["cover"]
        piece.name = row["name"]
        piece.artist = row["artist"]
        piece.is_favorite = bool(row["is_favorite"])
        playlist.music_pieces.append(piece)

    for row in conn.execute("SELECT * FROM playlist_shared_with ORDER BY playlist_uuid, position"):
        playlist = playlists.get(row["playlist_uuid"])
        if playlist is not None:
            playlist.shared_with.append({"username": row["username"], "canEdit": bool(row["can_edit"])})

    for row in conn.execute("SELECT * FROM playlist_saved_by ORDER BY playlist_uuid, position"):
        playlist = playlists.get(row["playlist_uuid"])
        if playlist is not None:
            playlist.saved_by.append(row["username"])

    for row in conn.execute("SELECT * FROM user_playlists_added_to"):
        user = users.get(row["username"])
        if user is not None:
            user.playlists_added_to[row["playlist_uuid"]] = {
                "owner": row["owner"],
                "playlistUUID": row["playlist_uuid"],
            }

    for row in conn.execute("SELECT * FROM user_saved_playlists"):
        user = users.get(row["username"])
        if user is not None:
            user.saved_playlists[row["playlist_uuid"]] = {
                "owner": row["owner"],
                "playlistUUID": row["playlist_uuid"],
            }

    conn.close()
    return users


def save_all_users(users: dict[str, User]) -> None:
    conn = get_connection()
    conn.execute("DELETE FROM users")
    conn.execute("DELETE FROM playlists")
    conn.execute("DELETE FROM music_pieces")
    conn.execute("DELETE FROM playlist_shared_with")
    conn.execute("DELETE FROM playlist_saved_by")
    conn.execute("DELETE FROM user_playlists_added_to")
    conn.execute("DELETE FROM user_saved_playlists")

    for user in users.values():
        conn.execute(
            "INSERT INTO users (username, profile_picture, password_hash, last_playback) VALUES (?, ?, ?, ?)",
            (user.username, user.profile_picture, user.password_hash, json.dumps(user.last_playback)),
        )

        for playlist in user.playlists.values():
            conn.execute(
                "INSERT INTO playlists (uuid, owner, name, cover, is_public) VALUES (?, ?, ?, ?, ?)",
                (playlist.UUID, playlist.owner, playlist.name, playlist.playlist_cover, int(playlist.is_public)),
            )

            for position, piece in enumerate(playlist.music_pieces):
                conn.execute(
                    """INSERT INTO music_pieces
                       (uuid, playlist_uuid, position, mp3_file, cover, name, artist, is_favorite)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        piece.UUID,
                        playlist.UUID,
                        position,
                        piece.mp3_file,
                        piece.cover,
                        piece.name,
                        piece.artist,
                        int(piece.is_favorite),
                    ),
                )

            for position, shared in enumerate(playlist.shared_with):
                conn.execute(
                    "INSERT INTO playlist_shared_with (playlist_uuid, username, can_edit, position) VALUES (?, ?, ?, ?)",
                    (playlist.UUID, shared["username"], int(shared["canEdit"]), position),
                )

            for position, saved_username in enumerate(playlist.saved_by):
                conn.execute(
                    "INSERT INTO playlist_saved_by (playlist_uuid, username, position) VALUES (?, ?, ?)",
                    (playlist.UUID, saved_username, position),
                )

        for entry in user.playlists_added_to.values():
            conn.execute(
                "INSERT INTO user_playlists_added_to (username, playlist_uuid, owner) VALUES (?, ?, ?)",
                (user.username, entry["playlistUUID"], entry["owner"]),
            )

        for entry in user.saved_playlists.values():
            conn.execute(
                "INSERT INTO user_saved_playlists (username, playlist_uuid, owner) VALUES (?, ?, ?)",
                (user.username, entry["playlistUUID"], entry["owner"]),
            )

    conn.commit()
    conn.close()
