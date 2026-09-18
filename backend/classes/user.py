from typing import Any

import bcrypt

from backend.classes.music_piece import MusicPiece
from backend.classes.playlist import Playlist
from backend.utils.playlist import randomize_playlist


class User:

    def __init__(self, username: str, raw_password: str) -> None:
        self.profile_picture: str = "/default_profile_picture.svg"
        self.username: str = username
        self.password_hash: bytes = self._hash_password(raw_password)
        self.playlists: dict[str, Playlist] = {}

        self.playlists_added_to : dict[ str, dict[str, str]] = {}
        self.saved_playlists: dict[ str, dict[str, str]] = {}

        self.last_playback : dict = {
            'repeatOn': False,
            'shuffleOn': False,
            'playlistUUID': None,
            'musicPieceUUID': None,
            'position': 0,
            'current_playlist_index': None
        }

    def __str__(self) -> str:
        return f"User(username={self.username}, profile_picture={self.profile_picture}, playlists={list(self.playlists.keys())})"

    @staticmethod
    def _hash_password(raw_password: str) -> bytes:
        return bcrypt.hashpw(raw_password.encode('utf-8'), bcrypt.gensalt())

    def check_password(self, input_password: str) -> bool:
        return bcrypt.checkpw(input_password.encode('utf-8'), self.password_hash)

    def update_password(self, new_password: str) -> None:
        self.password_hash = self._hash_password(new_password)

    def add_playlist(self, playlist_uuid : str, playlist: Playlist) -> None:
        self.playlists[playlist_uuid] = playlist

    def update_profile_picture(self, picture_url: str) -> None:
        self.profile_picture = picture_url

    def update_username(self, new_username: str) -> None:
        self.username = new_username
        for playlist in self.playlists.values():
            playlist.update_owner(new_username)

    def update_last_playback(self, playback_data: dict[Any, Any]) -> None:
        self.last_playback = {
            'repeatOn': playback_data.get('repeatOn', False),
            'shuffleOn': playback_data.get('shuffleOn', False),
            'playlistUUID': playback_data.get('playlist_uuid'),
            'musicPieceUUID': playback_data.get('current_music_piece_uuid'),
            'position': playback_data.get('position', 0),
            'current_playlist_index': playback_data.get('current_playlist_index')
        }

    def reset_last_playback(self) -> None:
        self.last_playback = {
            'repeatOn': False,
            'shuffleOn': False,
            'playlistUUID': None,
            'musicPieceUUID': None,
            'position': 0,
            'current_playlist_index': None
        }

    def get_last_playback(self) -> dict[Any, Any] | None:
        payload = {}

        playlist_uuid = self.last_playback.get('playlistUUID')
        if playlist_uuid is None: return None

        user_playlist = self.playlists.get(playlist_uuid)
        if user_playlist is None: return None

        start_index = self.last_playback.get('current_playlist_index')
        if start_index < 0 or start_index >= len(user_playlist): return None

        shuffle_status = self.last_playback.get('shuffleOn')
        if shuffle_status: queue = randomize_playlist(user_playlist, start_index)
        else: queue = user_playlist.music_pieces

        payload['queue'] = [piece.to_dict() for piece in queue]
        payload['position'] = self.last_playback.get('position')
        payload['repeatOn'] = self.last_playback.get('repeatOn')
        payload['shuffleOn'] = shuffle_status
        payload['currentPlaylistUUID'] = playlist_uuid
        payload['currentPlaylistIndex'] = self.last_playback.get('current_playlist_index')
        payload['orderedPlaylist'] = [piece.to_dict() for piece in self.playlists[playlist_uuid].music_pieces]
        return payload

    def add_public_playlist_to_library(self, playlist_uuid : str, playlist_owner : str) -> None:
        if playlist_uuid not in self.saved_playlists:
            self.saved_playlists[playlist_uuid] = {'owner': playlist_owner, 'playlistUUID': playlist_uuid}

    def remove_public_playlist_from_library(self, playlist_uuid: str) -> None:
        del self.saved_playlists[playlist_uuid]

    def get_saved_playlists(self) -> dict[str, dict]:
        saved_playlists = {}
        for saved_playlist in self.saved_playlists.values():
            resolved_playlist = self._locate_playlist_for_user(saved_playlist)
            if resolved_playlist is not None:
                saved_playlists[saved_playlist['playlistUUID']] = resolved_playlist
        return saved_playlists

    def get_playlists_added_to(self) -> dict[str, dict]:
        added_playlists = {}
        for added_playlist in self.playlists_added_to.values():
            resolved_playlist = self._locate_playlist_for_user(added_playlist)
            if resolved_playlist is not None:
                added_playlists[added_playlist['playlistUUID']] = resolved_playlist
        return added_playlists

    def add_playlist_added_to(self, playlist_uuid: str, playlist_owner : str) -> None:
        if playlist_uuid not in self.playlists_added_to:
            self.playlists_added_to[playlist_uuid] = {'owner': playlist_owner, 'playlistUUID': playlist_uuid}
        if playlist_uuid in self.saved_playlists:
            del self.saved_playlists[playlist_uuid]

    def remove_playlist_added_to(self, playlist_uuid: str) -> None:
        if playlist_uuid in self.playlists_added_to:
            del self.playlists_added_to[playlist_uuid]

    def to_dict(self) -> dict[str, Any]:
        return {
            "username": self.username,
            "profilePicture": self.profile_picture,
            "playlists": {uuid: playlist.to_dict() for uuid, playlist in self.playlists.items()} | self.get_playlists_added_to() | self.get_saved_playlists()
        }

    @staticmethod
    def _locate_playlist_for_user(playlist : dict[str, str]) -> dict[str, Any] | None:
        from backend.instances import user_manager
        owner = playlist.get('owner')
        if owner is None: return None
        playlist_owner = user_manager.search(owner)
        if playlist_owner is None: return None
        playlist_uuid = playlist.get('playlistUUID')
        if playlist_uuid is None: return None
        playlist_obj = playlist_owner.playlists.get(playlist_uuid)
        return playlist_obj.to_dict() if playlist_obj else None