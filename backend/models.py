from sqlalchemy import ForeignKey, MetaData
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING_CONVENTION)


class UserModel(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(unique=True, index=True)
    profile_picture: Mapped[str | None]
    password_hash: Mapped[bytes | None]
    last_playback: Mapped[str | None]

    playlists: Mapped[list["PlaylistModel"]] = relationship(back_populates="owner_user")
    playlists_added_to: Mapped[list["UserPlaylistAddedTo"]] = relationship(
        back_populates="user", foreign_keys="UserPlaylistAddedTo.user_id"
    )
    saved_playlists: Mapped[list["UserSavedPlaylist"]] = relationship(
        back_populates="user", foreign_keys="UserSavedPlaylist.user_id"
    )


class PlaylistModel(Base):
    __tablename__ = "playlists"

    uuid: Mapped[str] = mapped_column(primary_key=True)
    owner_id: Mapped[str | None] = mapped_column(ForeignKey("users.id"), index=True)
    name: Mapped[str | None]
    cover: Mapped[str | None]
    is_public: Mapped[int | None]

    owner_user: Mapped["UserModel"] = relationship(back_populates="playlists")
    music_pieces: Mapped[list["MusicPieceModel"]] = relationship(
        back_populates="playlist", order_by="MusicPieceModel.position", cascade="all, delete-orphan"
    )
    shared_with: Mapped[list["PlaylistSharedWith"]] = relationship(
        back_populates="playlist", order_by="PlaylistSharedWith.position", cascade="all, delete-orphan"
    )
    saved_by: Mapped[list["PlaylistSavedBy"]] = relationship(
        back_populates="playlist", order_by="PlaylistSavedBy.position", cascade="all, delete-orphan"
    )


class MusicPieceModel(Base):
    __tablename__ = "music_pieces"

    uuid: Mapped[str] = mapped_column(primary_key=True)
    playlist_uuid: Mapped[str | None] = mapped_column(ForeignKey("playlists.uuid"), index=True)
    position: Mapped[int | None]
    mp3_file: Mapped[str | None]
    cover: Mapped[str | None]
    name: Mapped[str | None]
    artist: Mapped[str | None]
    is_favorite: Mapped[int | None]

    playlist: Mapped["PlaylistModel"] = relationship(back_populates="music_pieces")


class PlaylistSharedWith(Base):
    __tablename__ = "playlist_shared_with"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    playlist_uuid: Mapped[str] = mapped_column(ForeignKey("playlists.uuid"), index=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    can_edit: Mapped[int | None]
    position: Mapped[int | None]

    playlist: Mapped["PlaylistModel"] = relationship(back_populates="shared_with")


class PlaylistSavedBy(Base):
    __tablename__ = "playlist_saved_by"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    playlist_uuid: Mapped[str] = mapped_column(ForeignKey("playlists.uuid"), index=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    position: Mapped[int | None]

    playlist: Mapped["PlaylistModel"] = relationship(back_populates="saved_by")


class UserPlaylistAddedTo(Base):
    __tablename__ = "user_playlists_added_to"

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    playlist_uuid: Mapped[str] = mapped_column(primary_key=True)
    owner_id: Mapped[str | None] = mapped_column(ForeignKey("users.id"), index=True)

    user: Mapped["UserModel"] = relationship(back_populates="playlists_added_to", foreign_keys=[user_id])


class UserSavedPlaylist(Base):
    __tablename__ = "user_saved_playlists"

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    playlist_uuid: Mapped[str] = mapped_column(primary_key=True)
    owner_id: Mapped[str | None] = mapped_column(ForeignKey("users.id"), index=True)

    user: Mapped["UserModel"] = relationship(back_populates="saved_playlists", foreign_keys=[user_id])
