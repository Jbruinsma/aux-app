"""add uuid primary key to users

Revision ID: 41c590e2c858
Revises: aea7622e74b4
Create Date: 2026-09-19 13:23:51.041005

"""
import uuid as uuid_lib
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '41c590e2c858'
down_revision: Union[str, Sequence[str], None] = 'aea7622e74b4'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    conn = op.get_bind()

    # SQLite auto-rewrites a dependent table's FK text when the referenced table is
    # renamed, so every FK pointing at `users` must be dropped before `users` itself
    # is renamed/recreated below - otherwise those tables end up referencing a
    # transient table name that no longer exists.
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.drop_constraint('fk_playlists_owner_users', type_='foreignkey')
        batch_op.drop_index('ix_playlists_owner')
    with op.batch_alter_table('user_playlists_added_to', schema=None) as batch_op:
        batch_op.drop_constraint('fk_user_playlists_added_to_username_users', type_='foreignkey')
    with op.batch_alter_table('user_saved_playlists', schema=None) as batch_op:
        batch_op.drop_constraint('fk_user_saved_playlists_username_users', type_='foreignkey')

    # ---- users: swap PK from username to a generated uuid id ----
    user_rows = conn.execute(sa.text(
        "SELECT username, profile_picture, password_hash, last_playback FROM users"
    )).fetchall()
    username_to_id = {row.username: str(uuid_lib.uuid4()) for row in user_rows}

    op.rename_table('users', '_users_old')
    op.create_table(
        'users',
        sa.Column('id', sa.String(), primary_key=True),
        sa.Column('username', sa.String(), nullable=False),
        sa.Column('profile_picture', sa.String(), nullable=True),
        sa.Column('password_hash', sa.LargeBinary(), nullable=True),
        sa.Column('last_playback', sa.String(), nullable=True),
    )
    op.create_index('ix_users_username', 'users', ['username'], unique=True)
    for row in user_rows:
        conn.execute(
            sa.text(
                "INSERT INTO users (id, username, profile_picture, password_hash, last_playback) "
                "VALUES (:id, :username, :profile_picture, :password_hash, :last_playback)"
            ),
            {
                "id": username_to_id[row.username],
                "username": row.username,
                "profile_picture": row.profile_picture,
                "password_hash": row.password_hash,
                "last_playback": row.last_playback,
            },
        )
    op.drop_table('_users_old')

    # ---- playlists.owner -> owner_id ----
    playlist_owners = conn.execute(sa.text("SELECT uuid, owner FROM playlists")).fetchall()
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.add_column(sa.Column('owner_id', sa.String(), nullable=True))
    for row in playlist_owners:
        conn.execute(
            sa.text("UPDATE playlists SET owner_id = :oid WHERE uuid = :uuid"),
            {"oid": username_to_id.get(row.owner), "uuid": row.uuid},
        )
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.drop_column('owner')
        batch_op.create_index('ix_playlists_owner_id', ['owner_id'])
        batch_op.create_foreign_key('fk_playlists_owner_id_users', 'users', ['owner_id'], ['id'])

    # ---- playlist_shared_with.username -> user_id ----
    shared_rows = conn.execute(sa.text("SELECT id, username FROM playlist_shared_with")).fetchall()
    with op.batch_alter_table('playlist_shared_with', schema=None) as batch_op:
        batch_op.add_column(sa.Column('user_id', sa.String(), nullable=True))
    for row in shared_rows:
        conn.execute(
            sa.text("UPDATE playlist_shared_with SET user_id = :uid WHERE id = :id"),
            {"uid": username_to_id.get(row.username), "id": row.id},
        )
    with op.batch_alter_table('playlist_shared_with', schema=None) as batch_op:
        batch_op.alter_column('user_id', nullable=False)
        batch_op.drop_column('username')
        batch_op.create_index('ix_playlist_shared_with_user_id', ['user_id'])
        batch_op.create_foreign_key('fk_playlist_shared_with_user_id_users', 'users', ['user_id'], ['id'])

    # ---- playlist_saved_by.username -> user_id ----
    saved_rows = conn.execute(sa.text("SELECT id, username FROM playlist_saved_by")).fetchall()
    with op.batch_alter_table('playlist_saved_by', schema=None) as batch_op:
        batch_op.add_column(sa.Column('user_id', sa.String(), nullable=True))
    for row in saved_rows:
        conn.execute(
            sa.text("UPDATE playlist_saved_by SET user_id = :uid WHERE id = :id"),
            {"uid": username_to_id.get(row.username), "id": row.id},
        )
    with op.batch_alter_table('playlist_saved_by', schema=None) as batch_op:
        batch_op.alter_column('user_id', nullable=False)
        batch_op.drop_column('username')
        batch_op.create_index('ix_playlist_saved_by_user_id', ['user_id'])
        batch_op.create_foreign_key('fk_playlist_saved_by_user_id_users', 'users', ['user_id'], ['id'])

    # ---- user_playlists_added_to: composite PK (username, playlist_uuid) -> (user_id, playlist_uuid) ----
    added_rows = conn.execute(sa.text(
        "SELECT username, playlist_uuid, owner FROM user_playlists_added_to"
    )).fetchall()
    op.rename_table('user_playlists_added_to', '_user_playlists_added_to_old')
    op.create_table(
        'user_playlists_added_to',
        sa.Column('user_id', sa.String(), sa.ForeignKey('users.id', name='fk_user_playlists_added_to_user_id_users'), primary_key=True),
        sa.Column('playlist_uuid', sa.String(), primary_key=True),
        sa.Column('owner_id', sa.String(), sa.ForeignKey('users.id', name='fk_user_playlists_added_to_owner_id_users'), nullable=True),
    )
    op.create_index('ix_user_playlists_added_to_owner_id', 'user_playlists_added_to', ['owner_id'])
    for row in added_rows:
        conn.execute(
            sa.text(
                "INSERT INTO user_playlists_added_to (user_id, playlist_uuid, owner_id) "
                "VALUES (:uid, :playlist_uuid, :oid)"
            ),
            {
                "uid": username_to_id.get(row.username),
                "playlist_uuid": row.playlist_uuid,
                "oid": username_to_id.get(row.owner),
            },
        )
    op.drop_table('_user_playlists_added_to_old')

    # ---- user_saved_playlists: composite PK (username, playlist_uuid) -> (user_id, playlist_uuid) ----
    saved_playlist_rows = conn.execute(sa.text(
        "SELECT username, playlist_uuid, owner FROM user_saved_playlists"
    )).fetchall()
    op.rename_table('user_saved_playlists', '_user_saved_playlists_old')
    op.create_table(
        'user_saved_playlists',
        sa.Column('user_id', sa.String(), sa.ForeignKey('users.id', name='fk_user_saved_playlists_user_id_users'), primary_key=True),
        sa.Column('playlist_uuid', sa.String(), primary_key=True),
        sa.Column('owner_id', sa.String(), sa.ForeignKey('users.id', name='fk_user_saved_playlists_owner_id_users'), nullable=True),
    )
    op.create_index('ix_user_saved_playlists_owner_id', 'user_saved_playlists', ['owner_id'])
    for row in saved_playlist_rows:
        conn.execute(
            sa.text(
                "INSERT INTO user_saved_playlists (user_id, playlist_uuid, owner_id) "
                "VALUES (:uid, :playlist_uuid, :oid)"
            ),
            {
                "uid": username_to_id.get(row.username),
                "playlist_uuid": row.playlist_uuid,
                "oid": username_to_id.get(row.owner),
            },
        )
    op.drop_table('_user_saved_playlists_old')


def downgrade() -> None:
    """Downgrade schema."""
    conn = op.get_bind()
    id_rows = conn.execute(sa.text("SELECT id, username FROM users")).fetchall()
    id_to_username = {row.id: row.username for row in id_rows}

    # Every FK pointing at `users` is rebuilt as a plain column first (no FK), the
    # `users` table is swapped back to a username PK, and the three FKs referencing
    # `users.username` are attached last - SQLite auto-rewrites a dependent table's
    # FK text when the referenced table is renamed, so nothing may hold a live FK to
    # `users` while it's being renamed/recreated.

    # ---- user_saved_playlists: back to (username, playlist_uuid), no FK yet ----
    saved_playlist_rows = conn.execute(sa.text(
        "SELECT user_id, playlist_uuid, owner_id FROM user_saved_playlists"
    )).fetchall()
    op.rename_table('user_saved_playlists', '_user_saved_playlists_new')
    op.create_table(
        'user_saved_playlists',
        sa.Column('username', sa.String(), primary_key=True),
        sa.Column('playlist_uuid', sa.String(), primary_key=True),
        sa.Column('owner', sa.String(), nullable=True),
    )
    for row in saved_playlist_rows:
        conn.execute(
            sa.text(
                "INSERT INTO user_saved_playlists (username, playlist_uuid, owner) "
                "VALUES (:username, :playlist_uuid, :owner)"
            ),
            {
                "username": id_to_username.get(row.user_id),
                "playlist_uuid": row.playlist_uuid,
                "owner": id_to_username.get(row.owner_id),
            },
        )
    op.drop_table('_user_saved_playlists_new')

    # ---- user_playlists_added_to: back to (username, playlist_uuid), no FK yet ----
    added_rows = conn.execute(sa.text(
        "SELECT user_id, playlist_uuid, owner_id FROM user_playlists_added_to"
    )).fetchall()
    op.rename_table('user_playlists_added_to', '_user_playlists_added_to_new')
    op.create_table(
        'user_playlists_added_to',
        sa.Column('username', sa.String(), primary_key=True),
        sa.Column('playlist_uuid', sa.String(), primary_key=True),
        sa.Column('owner', sa.String(), nullable=True),
    )
    for row in added_rows:
        conn.execute(
            sa.text(
                "INSERT INTO user_playlists_added_to (username, playlist_uuid, owner) "
                "VALUES (:username, :playlist_uuid, :owner)"
            ),
            {
                "username": id_to_username.get(row.user_id),
                "playlist_uuid": row.playlist_uuid,
                "owner": id_to_username.get(row.owner_id),
            },
        )
    op.drop_table('_user_playlists_added_to_new')

    # ---- playlist_saved_by.user_id -> username (unaffected by the users PK swap) ----
    with op.batch_alter_table('playlist_saved_by', schema=None) as batch_op:
        batch_op.add_column(sa.Column('username', sa.String(), nullable=True))
    for row in conn.execute(sa.text("SELECT id, user_id FROM playlist_saved_by")).fetchall():
        conn.execute(
            sa.text("UPDATE playlist_saved_by SET username = :username WHERE id = :id"),
            {"username": id_to_username.get(row.user_id), "id": row.id},
        )
    with op.batch_alter_table('playlist_saved_by', schema=None) as batch_op:
        batch_op.alter_column('username', nullable=False)
        batch_op.drop_constraint('fk_playlist_saved_by_user_id_users', type_='foreignkey')
        batch_op.drop_index('ix_playlist_saved_by_user_id')
        batch_op.drop_column('user_id')

    # ---- playlist_shared_with.user_id -> username (unaffected by the users PK swap) ----
    with op.batch_alter_table('playlist_shared_with', schema=None) as batch_op:
        batch_op.add_column(sa.Column('username', sa.String(), nullable=True))
    for row in conn.execute(sa.text("SELECT id, user_id FROM playlist_shared_with")).fetchall():
        conn.execute(
            sa.text("UPDATE playlist_shared_with SET username = :username WHERE id = :id"),
            {"username": id_to_username.get(row.user_id), "id": row.id},
        )
    with op.batch_alter_table('playlist_shared_with', schema=None) as batch_op:
        batch_op.alter_column('username', nullable=False)
        batch_op.drop_constraint('fk_playlist_shared_with_user_id_users', type_='foreignkey')
        batch_op.drop_index('ix_playlist_shared_with_user_id')
        batch_op.drop_column('user_id')

    # ---- playlists.owner_id -> owner, no FK yet ----
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.add_column(sa.Column('owner', sa.String(), nullable=True))
    for row in conn.execute(sa.text("SELECT uuid, owner_id FROM playlists")).fetchall():
        conn.execute(
            sa.text("UPDATE playlists SET owner = :owner WHERE uuid = :uuid"),
            {"owner": id_to_username.get(row.owner_id), "uuid": row.uuid},
        )
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.drop_constraint('fk_playlists_owner_id_users', type_='foreignkey')
        batch_op.drop_index('ix_playlists_owner_id')
        batch_op.drop_column('owner_id')
        batch_op.create_index('ix_playlists_owner', ['owner'])

    # ---- users: drop id, restore username as PK ----
    user_rows = conn.execute(sa.text(
        "SELECT username, profile_picture, password_hash, last_playback FROM users"
    )).fetchall()
    op.rename_table('users', '_users_new')
    op.create_table(
        'users',
        sa.Column('username', sa.String(), primary_key=True),
        sa.Column('profile_picture', sa.String(), nullable=True),
        sa.Column('password_hash', sa.LargeBinary(), nullable=True),
        sa.Column('last_playback', sa.String(), nullable=True),
    )
    for row in user_rows:
        conn.execute(
            sa.text(
                "INSERT INTO users (username, profile_picture, password_hash, last_playback) "
                "VALUES (:username, :profile_picture, :password_hash, :last_playback)"
            ),
            {
                "username": row.username,
                "profile_picture": row.profile_picture,
                "password_hash": row.password_hash,
                "last_playback": row.last_playback,
            },
        )
    op.drop_table('_users_new')

    # ---- attach the three FKs referencing users.username, now that it's final ----
    with op.batch_alter_table('playlists', schema=None) as batch_op:
        batch_op.create_foreign_key('fk_playlists_owner_users', 'users', ['owner'], ['username'])
    with op.batch_alter_table('user_playlists_added_to', schema=None) as batch_op:
        batch_op.create_foreign_key('fk_user_playlists_added_to_username_users', 'users', ['username'], ['username'])
    with op.batch_alter_table('user_saved_playlists', schema=None) as batch_op:
        batch_op.create_foreign_key('fk_user_saved_playlists_username_users', 'users', ['username'], ['username'])
