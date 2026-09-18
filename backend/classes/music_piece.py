from typing import Any


class MusicPiece:

    def __init__(self, uuid: str, name: str, file_name: str, cover=None, is_favorite: bool = False) -> None:
        self.UUID: str = uuid
        self.mp3_file: str = f"/uploads/mp3s/{file_name}"
        self.cover = cover or "/icon.svg"
        self.name: str = name
        self.artist : str | None = None
        self.is_favorite: bool = is_favorite

    def to_dict(self) -> dict[str, Any]:
        return {
            "uuid": self.UUID,
            "title": self.name,
            "artist": self.artist,
            "cover": self.cover,
            "audio": self.mp3_file
        }