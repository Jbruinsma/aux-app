# PROFILE INFO, FOLLOWERS, FOLLOWING, LIKES, POSTS, COMMENTS
import os
import re
from threading import Thread

from fastapi import APIRouter, UploadFile, File
from fastapi.responses import JSONResponse
from werkzeug.utils import secure_filename

from backend.classes.user_manager import save_user_manager
from backend.config import PFPS_DIR
from backend.instances import user_manager

users_router = APIRouter()

@users_router.get('/check-username/{username}')
def check_username_exists(username: str):
    user_exists = user_manager.contains_key(username)
    return {"exists": user_exists}

@users_router.get('/{username}/delete')
def delete_user(username: str):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=404)

    user_manager.delete(username)
    save_user_manager(user_manager)

    Thread(
        target= traverse_and_delete_user,
        args= username,
        daemon= True
    ).start()

    return JSONResponse({"message": "User deleted successfully"}, status_code=200)

@users_router.get('/profile/{username}')
def get_profile(username: str):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=200)

    return JSONResponse(user.to_dict(), status_code=200)

@users_router.post('/{username}/update/profile-picture')
def update_profile_picture(username: str, profile_picture: UploadFile = File(None)):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=404)

    file = profile_picture
    if not file: return JSONResponse({"error": "No file uploaded"}, status_code=400)

    filename = secure_filename(file.filename)
    with open(os.path.join(PFPS_DIR, filename), "wb") as out:
        out.write(file.file.read())

    file_path = f"/uploads/pfps/{filename}"
    user.update_profile_picture(file_path)

    save_user_manager(user_manager)

    return JSONResponse({
        "message": "Profile picture updated",
        "profile_picture_url": file_path
    }, status_code=200)

@users_router.post('/{username}/update-username/{new_username}')
def update_username(username: str, new_username: str):
    if not re.match(r'^[a-zA-Z0-9_]{3,20}$', new_username):
        return JSONResponse({"error": "Invalid username format"}, status_code=200)

    if user_manager.contains_key(new_username):
        return JSONResponse({"error": "Username already exists"}, status_code=200)

    if username == new_username:
        return JSONResponse({"message": "Username unchanged"}, status_code=200)

    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    success = user_manager.update_key(username, new_username)
    if not success:
        return JSONResponse({"error": "Failed to update username"}, status_code=200)

    user.update_username(new_username)
    save_user_manager(user_manager)

    Thread(
        target= traverse_and_patch_playlists,
        args=(username, new_username),
        daemon=True
    ).start()

    return JSONResponse({
        "message": "Username updated",
        "new_username": new_username
    }, status_code=200)


@users_router.post('/{username}/update-password')
def update_password(username: str, data: dict):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=400)

    old_password = data.get('old_password')
    new_password = data.get('new_password')

    if not old_password or not new_password:
        return JSONResponse({"error": "Old and new passwords are required"}, status_code=400)

    if not user.check_password(old_password):
        return JSONResponse({"error": "Current password is incorrect"}, status_code=400)

    user.update_password(new_password)
    save_user_manager(user_manager)

    return JSONResponse({"message": "Password updated successfully"}, status_code=200)

@users_router.get('/{username}/get-last-playback')
def get_last_playback(username: str):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=404)
    last_playback = user.get_last_playback()
    print("RETURNING LAST PLAYBACK: ", last_playback)
    return JSONResponse(last_playback, status_code=200)

@users_router.post('/{username}/update-last-playback')
def update_last_playback(username: str, data: dict):
    print(f"Updating last playback for user: {username}")
    user = user_manager.search(username)
    if not user:
        print(f"User {username} not found in user manager.")
        return JSONResponse({"error": "User not found"}, status_code=404)
    print(f"Updating last playback for {username}: {data}")
    user.update_last_playback(data)
    save_user_manager(user_manager)
    return JSONResponse({"message": "Last playback updated successfully"}, status_code=200)

@users_router.post('/{username}/add-public-playlist')
def add_public_playlist_to_library(username: str, data: dict):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')

    if not playlist_uuid:
        return JSONResponse({"error": "Playlist UUID is required"}, status_code=200)

    if not owner_username:
        return JSONResponse({"error": "Playlist owner is required"}, status_code=200)

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None:
        return JSONResponse({"error": "Playlist owner not found"}, status_code=200)

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

    playlist.add_to_saved_by(username)
    user.add_public_playlist_to_library(playlist_uuid= playlist_uuid, playlist_owner= owner_username)
    save_user_manager(user_manager)
    return JSONResponse({"message": "Playlist added to library successfully", "savedBy": playlist.saved_by}, status_code=200)

@users_router.post('/{username}/remove-public-playlist')
def remove_public_playlist_form_library(username: str, data: dict):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')
    if not playlist_uuid: return JSONResponse({"error": "Playlist UUID is required"}, status_code=200)
    if not owner_username: return JSONResponse({"error": "Playlist owner is required"}, status_code=200)

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None:
        return JSONResponse({"error": "Playlist owner not found"}, status_code=200)

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

    playlist.remove_from_saved_by(username)
    user.remove_public_playlist_from_library(playlist_uuid= playlist_uuid)
    save_user_manager(user_manager)

    return JSONResponse({"message": "Playlist removed from library successfully", "savedBy": playlist.saved_by, "added": playlist.shared_with }, status_code=200)

@users_router.post('/{username}/remove-added-to-playlist')
def remove_added_to_playlist(username: str, data: dict):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    playlist_uuid = data.get('playlist_uuid')
    owner_username = data.get('playlist_owner')
    if not owner_username: return JSONResponse({"error": "Playlist owner is required"}, status_code=200)
    if not playlist_uuid: return JSONResponse({"error": "Playlist UUID is required"}, status_code=200)

    playlist_owner = user_manager.search(owner_username)
    if playlist_owner is None: return JSONResponse({"error": "Playlist owner not found"}, status_code=200)

    playlist = playlist_owner.playlists.get(playlist_uuid)
    if playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=200)

    playlist.remove_from_shared_list(username)
    user.remove_playlist_added_to(playlist_uuid= playlist_uuid)
    save_user_manager(user_manager)

    return JSONResponse({"message": "Playlist removed from added to successfully", "savedBy": playlist.saved_by, "added": playlist.shared_with }, status_code=200)

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
