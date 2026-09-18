# CREATE, EDIT, DELETE, and GET playlists
import os
import json
import random

from flask import Blueprint, request, jsonify, json
from werkzeug.utils import secure_filename

from backend.classes.music_piece import MusicPiece
from backend.classes.playlist import Playlist
from backend.classes.user_manager import save_user_manager
from backend.config import COVERS_DIR, MP3S_DIR
from backend.instances import user_manager
from backend.utils.playlist import randomize_playlist

playlists_bp = Blueprint('playlists', __name__)

@playlists_bp.route('/<username>/<playlist_id>', methods=['GET'])
def get_playlist(username, playlist_id):

    user = user_manager.search(username)

    if not user:
        return jsonify({"error": "User not found"}), 200

    try:
        playlist = user.playlists[playlist_id]
        print(f"Retrieved playlist: {playlist}")
    except KeyError:
        return jsonify({"error": "Playlist not found"}), 200

    return jsonify(playlist.to_dict() | {  'username': username }), 200

@playlists_bp.route('/<username>/create', methods=['POST'])
def add_mp3s_to_playlist(username):
    cover_file = request.files.get('cover')

    if cover_file:
        print("Received cover file:", cover_file.filename)
        filename = secure_filename(cover_file.filename)
        cover_file.save(os.path.join(COVERS_DIR, filename))
        file_name = f"/uploads/covers/{filename}"
    else:
        print("No cover file received.")
        file_name = None

    new_playlist = Playlist(uuid= request.form.get('uuid'),
                            owner=request.form.get('owner'),
                            name=request.form.get('name'),
                            is_public=request.form.get('isPublic') == 'true',
                            playlist_cover= file_name
                            )

    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 200

    user.add_playlist(playlist_uuid=request.form.get('uuid'), playlist=new_playlist)
    save_user_manager(user_manager)
    return { "status": "success", "message": "Form data received!" }, 200

@playlists_bp.route('/<username>/<playlist_id>/delete', methods=['POST'])
def delete_playlist(username, playlist_id):
    user = user_manager.search(username)

    if not user:
        return jsonify({"error": "User not found"}), 200

    try:
        del user.playlists[playlist_id]
        save_user_manager(user_manager)
        return jsonify({"status": "success", "message": "Playlist deleted successfully"}), 200
    except KeyError:
        return jsonify({"error": "Playlist not found"}), 200

@playlists_bp.route('/<username>/<playlist_id>/edit', methods=['POST'])
def edit_playlist(username, playlist_id):
    user = user_manager.search(username)
    if not user:return jsonify({"error": "User not found"}), 200

    playlist_changes = request.form

    user_playlist = user.playlists[playlist_id]
    if user_playlist is None: return jsonify({"error": "Playlist not found"}), 200

    cover_file = request.files.get('cover')
    new_name = playlist_changes.get('name')
    is_public = True if playlist_changes.get('isPublic') == 'true' else False

    print(playlist_changes.get('isPublic'))
    print(f"IS PUBLIC: {is_public}")

    music_deleted = json.loads(playlist_changes.get('musicDeleted', '[]'))

    if cover_file:
        filename = secure_filename(cover_file.filename)
        cover_file.save(os.path.join(COVERS_DIR, filename))
        file_name = f"/uploads/covers/{filename}"
    else:
        file_name = user_playlist.playlist_cover

    user_playlist.update_playlist(name= new_name, is_public= is_public, playlist_cover= file_name, pieces_deleted= music_deleted)

    if not is_public:
        print("THIS SHOULD NOT EXECUTE")
        for user in user_playlist.saved_by:
            user_obj = user_manager.search(user)
            user_obj.remove_public_playlist_from_library(playlist_uuid= user_playlist.UUID)
        user_playlist.saved_by = []

    save_user_manager(user_manager)

    return jsonify({"status": "success", "message": "Playlist updated successfully"}), 200

@playlists_bp.route('/<username>/<playlist_id>/add', methods=['POST'])
def add_music_piece_to_playlist(username, playlist_id):
    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 200

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return jsonify({"error": "Playlist not found"}), 200

    mp3_files = request.files.getlist('mp3s')
    uuids = json.loads(request.form.get('uuids', '[]'))

    if len(mp3_files) != len(uuids): return jsonify({"error": "Mismatch between files and UUIDs"}), 200

    for mp3_file, uuid in zip(mp3_files, uuids):
        filename = secure_filename(mp3_file.filename)
        save_path = os.path.join(MP3S_DIR, filename)
        mp3_file.save(save_path)
        print(f"Saved: {filename} as UUID: {uuid}")

        new_music_piece = MusicPiece(
            uuid=uuid,
            name=mp3_file.filename,
            file_name=filename
        )
        user_playlist.add_music_piece(new_music_piece)
    save_user_manager(user_manager)
    return jsonify({"status": "success"}), 200

@playlists_bp.route('/<username>/<playlist_id>/add/friends', methods=['POST'])
def add_friends_to_playlist(username, playlist_id):

    print(f"Adding friends to playlist {playlist_id} for user {username}")

    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 404

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return jsonify({"error": "Playlist not found"}), 404


    data = request.get_json()
    print("Adding friends to playlist: ", data.get('friends'))
    user_playlist.shared_with = data.get('friends')


    for friend in user_playlist.shared_with:
        print(f"FRIEND: {friend}")
        if friend['username'] in user_playlist.saved_by: user_playlist.saved_by.remove(friend['username'])
        user_obj = user_manager.search(friend['username'])
        if user_obj is None: return jsonify({"error": f"Friend {friend['username']} not found"}), 404
        user_obj.add_playlist_added_to(playlist_uuid= playlist_id, playlist_owner= username)
        print(f"{user_obj.playlists_added_to}")

    save_user_manager(user_manager)
    return jsonify({"status": "success", "message": "Friends added to playlist"}), 200

@playlists_bp.route('/summary/<username>/<playlist_id>/music_piece/<int:index>/<mp3_uuid>', methods=['GET'])
def get_music_piece_summary(username, playlist_id, index, mp3_uuid):
    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 404

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return jsonify({"error": "Playlist not found"}), 404

    if index < 0 or index >= len(user_playlist.music_pieces): return jsonify({"error": "Index out of range"}), 400

    music_piece = user_playlist.music_pieces[index]
    if music_piece.UUID != mp3_uuid: return jsonify({"error": "Music piece UUID does not match"}), 400

    return jsonify(music_piece.to_dict()), 200

@playlists_bp.route('/update/<username>/<playlist_id>/music_piece/<int:index>/<mp3_uuid>', methods=['POST'])
def update_music_piece(username, playlist_id, index, mp3_uuid):
    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 404

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return jsonify({"error": "Playlist not found"}), 404

    if index < 0 or index >= len(user_playlist.music_pieces): return jsonify({"error": "Index out of range"}), 400

    music_piece = user_playlist.music_pieces[index]
    if music_piece.UUID != mp3_uuid: return jsonify({"error": "Music piece UUID does not match"}), 400

    new_name = request.form.get('name')
    new_cover = request.files.get('cover')
    new_artist = request.form.get('artist')

    print(f"{request.form}")

    if new_cover:
        filename = secure_filename(new_cover.filename)
        new_cover.save(os.path.join(COVERS_DIR, filename))
        music_piece.cover = f"/uploads/covers/{filename}"

    music_piece.artist = new_artist
    music_piece.name = new_name

    save_user_manager(user_manager)

    return jsonify({"status": "success", "message": "Music piece updated successfully", "musicPiece": music_piece.to_dict()}), 200

@playlists_bp.route('/<username>/<playlist_id>/play', methods=['GET'])
@playlists_bp.route('/<username>/<playlist_id>/play/<int:start_index>', methods=['GET'])
def play_playlist(username, playlist_id, start_index= None):
    shuffle = request.args.get('shuffle', 'false').lower() == 'true'
    print(f"Start index: {start_index}, shuffle: {shuffle}")

    if start_index is None: start_index = 0

    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    playlist = user.playlists.get(playlist_id)
    if not playlist:
        return jsonify({"error": "Playlist not found"}), 200

    if start_index < 0 or start_index >= len(playlist.music_pieces):
        return jsonify({"error": "Start index out of range"}), 200

    if shuffle: queue = randomize_playlist(playlist, start_index)
    else: queue = playlist.music_pieces

    return jsonify({
        'playlist':[piece.to_dict() for piece in queue],
        'startIndex': start_index,
        'currentPlaylistUUID': playlist.UUID,
        'orderedPlaylist': [piece.to_dict() for piece in playlist.music_pieces]
    }), 200

@playlists_bp.route('/<username>/<playlist_id>/play-shuffled/', methods=['GET'])
def play_playlist_shuffled(username, playlist_id):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    playlist = user.playlists.get(playlist_id)
    if not playlist:
        return jsonify({"error": "Playlist not found"}), 200

    randomized_starting_index = random.randint(0, len(playlist) - 1)
    queue = randomize_playlist(playlist, randomized_starting_index)

    return jsonify({
        'playlist': [piece.to_dict() for piece in queue],
        'startIndex': 0,
        'orderedStartingIndex': randomized_starting_index,
        'currentPlaylistUUID': playlist.UUID,
        'orderedPlaylist': [piece.to_dict() for piece in playlist.music_pieces]
    }), 200