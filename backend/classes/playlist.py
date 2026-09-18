from typing import Any

from backend.classes.music_piece import MusicPiece


class Playlist:

    def __init__(self, uuid : str, owner : str, name : str, is_public : bool = False, playlist_cover = None) -> None:
        self.UUID : str = uuid
        self.owner : str = owner
        self.name : str = name
        self.playlist_cover = playlist_cover or "/icon.svg"
        self.is_public : bool = is_public

        self.music_pieces: list[MusicPiece] = []
        self.shared_with: list[dict[str, bool]] = []
        self.saved_by: list[str] = []

    def __str__(self) -> str:
        return f"UUID: {self.UUID}, Name: {self.name}, Public? {self.is_public}, Pieces: {len(self.music_pieces)}, Shared with: {len(self.shared_with)}"

    def __len__(self) -> int:
        return len(self.music_pieces)

    def update_playlist(self, name : str, is_public : bool, playlist_cover : str, pieces_deleted : list[dict[str, str]]) -> None:
        self.update_playlist_name(name)
        if is_public != 'null':
            self.update_public_status(is_public)
        self.update_playlist_cover(playlist_cover)

        for deleted_piece in pieces_deleted:
            print(deleted_piece)
            self.delete_music_piece(deleted_piece['uuid'])

    def update_playlist_name(self, new_name: str) -> None:
        self.name = new_name

    def update_public_status(self, is_public: bool) -> None:
        self.is_public = is_public

    def update_playlist_cover(self, new_cover: str) -> None:
        if new_cover is None:
            self.playlist_cover = "/icon.svg"
        else:
            self.playlist_cover = new_cover

    def delete_music_piece(self, piece_uuid: str) -> None:
        for music_piece in self.music_pieces:
            if music_piece.UUID == piece_uuid:
                self.music_pieces.remove(music_piece)
                return

    def update_owner(self, new_owner: str) -> None:
        self.owner = new_owner

    def add_to_shared_list(self, username : str, can_edit : bool = False) -> None:
        self.shared_with.append({'username': username,'canEdit': can_edit})

    def remove_from_shared_list(self, username: str) -> None:
        for shared_user in self.shared_with:
            if shared_user['username'] == username:
                self.shared_with.remove(shared_user)
                return

    def add_to_saved_by(self, username: str) -> None:
        if username not in self.saved_by:
            self.saved_by.append(username)

    def remove_from_saved_by(self, username: str) -> None:
        if username in self.saved_by:
            self.saved_by.remove(username)

    def add_music_piece(self, music_piece: MusicPiece) -> None:
        if music_piece not in self.music_pieces:
            self.music_pieces.append(music_piece)

    def to_dict(self) -> dict[str, Any]:
        return  {
            "uuid": self.UUID,
            "owner": self.owner,
            "name": self.name,
            "isPublic": self.is_public,
            "cover": self.playlist_cover,
            "musicPieces": [piece.to_dict() for piece in self.music_pieces],
            "sharedWith": self.shared_with,
            "savedBy": self.saved_by
        }
