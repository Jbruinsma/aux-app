# PROFILE INFO, FOLLOWERS, FOLLOWING, LIKES, POSTS, COMMENTS
import os
import re
from threading import Thread

from flask import Blueprint, request, jsonify
from werkzeug.utils import secure_filename

from backend.classes.user_manager import save_user_manager
from backend.config import PFPS_DIR
from backend.instances import user_manager

users_bp = Blueprint('users', __name__)

@users_bp.route('/check-username/<username>', methods=['GET'])
def check_username_exists(username):
    user_exists = user_manager.contains_key(username)
    return jsonify({"exists": user_exists})

@users_bp.route('/<username>/delete', methods=['GET'])
def delete_user(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 404

    user_manager.delete(username)
    save_user_manager(user_manager)

    Thread(
        target= traverse_and_delete_user,
        args= username,
        daemon= True
    ).start()

    return jsonify({"message": "User deleted successfully"}), 200

@users_bp.route('/profile/<username>', methods=['GET'])
def get_profile(username):
    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 200

    return jsonify(user.to_dict()), 200

@users_bp.route('/<username>/update/profile-picture', methods=['POST'])
def update_profile_picture(username):
    user = user_manager.search(username)
    if not user: return jsonify({"error": "User not found"}), 404

    file = request.files.get('profile_picture')
    if not file: return jsonify({"error": "No file uploaded"}), 400

    filename = secure_filename(file.filename)
    file.save(os.path.join(PFPS_DIR, filename))

    file_path = f"/uploads/pfps/{filename}"
    user.update_profile_picture(file_path)

    save_user_manager(user_manager)

    return jsonify({
        "message": "Profile picture updated",
        "profile_picture_url": file_path
    }), 200

@users_bp.route('/<username>/update-username/<new_username>', methods=['POST'])
def update_username(username, new_username):
    if not re.match(r'^[a-zA-Z0-9_]{3,20}$', new_username):
        return jsonify({"error": "Invalid username format"}), 200

    if user_manager.contains_key(new_username):
        return jsonify({"error": "Username already exists"}), 200

    if username == new_username:
        return jsonify({"message": "Username unchanged"}), 200

    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    success = user_manager.update_key(username, new_username)
    if not success:
        return jsonify({"error": "Failed to update username"}), 200

    user.update_username(new_username)
    save_user_manager(user_manager)

    Thread(
        target= traverse_and_patch_playlists,
        args=(username, new_username),
        daemon=True
    ).start()

    return jsonify({
        "message": "Username updated",
        "new_username": new_username
    }), 200


@users_bp.route('/<username>/update-password', methods=['POST'])
def update_password(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 400

    data = request.get_json()
    old_password = data.get('old_password')
    new_password = data.get('new_password')

    if not old_password or not new_password:
        return jsonify({"error": "Old and new passwords are required"}), 400

    if not user.check_password(old_password):
        return jsonify({"error": "Current password is incorrect"}), 400

    user.update_password(new_password)
    save_user_manager(user_manager)

    return jsonify({"message": "Password updated successfully"}), 200

@users_bp.route('/<username>/get-last-playback', methods=['GET'])
def get_last_playback(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 404
    last_playback = user.get_last_playback()
    print("RETURNING LAST PLAYBACK: ", last_playback)
    return jsonify(last_playback), 200

@users_bp.route('/<username>/update-last-playback', methods=['POST'])
def update_last_playback(username):
    print(f"Updating last playback for user: {username}")
    user = user_manager.search(username)
    if not user:
        print(f"User {username} not found in user manager.")
        return jsonify({"error": "User not found"}), 404
    data = request.get_json()
    print(f"Updating last playback for {username}: {data}")
    user.update_last_playback(data)
    save_user_manager(user_manager)
    return jsonify({"message": "Last playback updated successfully"}), 200

@users_bp.route('/<username>/add-public-playlist', methods=['POST'])
def add_public_playlist_to_library(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    data = request.get_json()
    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')

    if not playlist_uuid:
        return jsonify({"error": "Playlist UUID is required"}), 200

    if not owner_username:
        return jsonify({"error": "Playlist owner is required"}), 200

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None:
        return jsonify({"error": "Playlist owner not found"}), 200

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None:
        return jsonify({"error": "Playlist not found"}), 200

    playlist.add_to_saved_by(username)
    user.add_public_playlist_to_library(playlist_uuid= playlist_uuid, playlist_owner= owner_username)
    save_user_manager(user_manager)
    return jsonify({"message": "Playlist added to library successfully", "savedBy": playlist.saved_by}), 200

@users_bp.route('/<username>/remove-public-playlist', methods=['POST'])
def remove_public_playlist_form_library(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    data = request.get_json()
    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')
    if not playlist_uuid: return jsonify({"error": "Playlist UUID is required"}), 200
    if not owner_username: return jsonify({"error": "Playlist owner is required"}), 200

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None:
        return jsonify({"error": "Playlist owner not found"}), 200

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None:
        return jsonify({"error": "Playlist not found"}), 200

    playlist.remove_from_saved_by(username)
    user.remove_public_playlist_from_library(playlist_uuid= playlist_uuid)
    save_user_manager(user_manager)

    return jsonify({"message": "Playlist removed from library successfully", "savedBy": playlist.saved_by, "added": playlist.shared_with }), 200

@users_bp.route('/<username>/remove-added-to-playlist', methods=['POST'])
def remove_added_to_playlist(username):
    user = user_manager.search(username)
    if not user:
        return jsonify({"error": "User not found"}), 200

    data = request.get_json()
    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')
    if not owner_username: return jsonify({"error": "Playlist owner is required"}), 200
    if not playlist_uuid: return jsonify({"error": "Playlist UUID is required"}), 200

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None: return jsonify({"error": "Playlist owner not found"}), 200

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None: return jsonify({"error": "Playlist not found"}), 200

    playlist.remove_from_shared_list(username)
    user.remove_playlist_added_to(playlist_uuid= playlist_uuid)
    save_user_manager(user_manager)

    return jsonify({"message": "Playlist removed from added to successfully", "savedBy": playlist.saved_by, "added": playlist.shared_with }), 200

def traverse_and_patch_playlists(old_username, new_username):
    for username, user_obj in user_manager.in_order_traversal():
        for pl in user_obj.playlists.values():
            for i in range(len(pl.shared_with)):
                shared_user = pl.shared_with[i]['username']
                if shared_user == old_username:
                    print(f"Updating shared_with from {old_username} to {new_username}")
                    pl.shared_with[i]['username'] = new_username

            for i in range(len(pl.saved_by)):
                saved_user = pl.saved_by[i]
                if saved_user == old_username:
                    print(f"Updating saved_by from {old_username} to {new_username}")
                    pl.saved_by[i] = new_username

def traverse_and_delete_user(deleted_user_username):
    for username, user_obj in user_manager.in_order_traversal():
        for pl in user_obj.playlists.values():
            for i in range(len(pl.shared_with)):
                shared_user = pl.shared_with[i]['username']
                if shared_user == deleted_user_username:
                    del pl.shared_with[i]

            for i in range(len(pl.saved_by)):
                saved_user = pl.saved_by[i]
                if saved_user == deleted_user_username:
                    del pl.saved_by[i]