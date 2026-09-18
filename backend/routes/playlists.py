# CREATE, EDIT, DELETE, and GET playlists
import os
import json
import random

from fastapi import APIRouter, Form, File, UploadFile
from fastapi.responses import JSONResponse
from werkzeug.utils import secure_filename

from backend.classes.music_piece import MusicPiece
from backend.classes.playlist import Playlist
from backend.classes.user_manager import save_user_manager
from backend.config import COVERS_DIR, MP3S_DIR
from backend.instances import user_manager
from backend.utils.playlist import randomize_playlist

playlists_router = APIRouter()

def _save_upload(file: UploadFile, directory: str) -> str:
    filename = secure_filename(file.filename)
    with open(os.path.join(directory, filename), "wb") as out:
        out.write(file.file.read())
    return filename

@playlists_router.get('/{username}/{playlist_id}')
def get_playlist(username: str, playlist_id: str):

    user = user_manager.search(username)

    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    try:
        playlist = user.playlists[playlist_id]
        print(f"Retrieved playlist: {playlist}")
    except KeyError:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

    return JSONResponse(playlist.to_dict() | {  'username': username }, status_code=200)

@playlists_router.post('/{username}/create')
def add_mp3s_to_playlist(
    username: str,
    uuid: str = Form(None),
    owner: str = Form(None),
    name: str = Form(None),
    isPublic: str = Form(None),
    cover: UploadFile = File(None),
):
    if cover:
        print("Received cover file:", cover.filename)
        filename = _save_upload(cover, COVERS_DIR)
        file_name = f"/uploads/covers/{filename}"
    else:
        print("No cover file received.")
        file_name = None

    new_playlist = Playlist(uuid= uuid,
                            owner=owner,
                            name=name,
                            is_public=isPublic == 'true',
                            playlist_cover= file_name
                            )

    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=200)

    user.add_playlist(playlist_uuid=uuid, playlist=new_playlist)
    save_user_manager(user_manager)
    return { "status": "success", "message": "Form data received!" }

@playlists_router.post('/{username}/{playlist_id}/delete')
def delete_playlist(username: str, playlist_id: str):
    user = user_manager.search(username)

    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    try:
        del user.playlists[playlist_id]
        save_user_manager(user_manager)
        return JSONResponse({"status": "success", "message": "Playlist deleted successfully"}, status_code=200)
    except KeyError:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

@playlists_router.post('/{username}/{playlist_id}/edit')
def edit_playlist(
    username: str,
    playlist_id: str,
    name: str = Form(None),
    isPublic: str = Form(None),
    musicDeleted: str = Form('[]'),
    cover: UploadFile = File(None),
):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=200)

    new_name = name
    is_public = True if isPublic == 'true' else False

    print(isPublic)
    print(f"IS PUBLIC: {is_public}")

    user_playlist = user.playlists[playlist_id]
    if user_playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=200)

    music_deleted = json.loads(musicDeleted)

    if cover:
        filename = _save_upload(cover, COVERS_DIR)
        file_name = f"/uploads/covers/{filename}"
    else:
        file_name = user_playlist.playlist_cover

    user_playlist.update_playlist(name= new_name, is_public= is_public, playlist_cover= file_name, pieces_deleted= music_deleted)

    if not is_public:
        print("THIS SHOULD NOT EXECUTE")
        for saved_username in user_playlist.saved_by:
            user_obj = user_manager.search(saved_username)
            user_obj.remove_public_playlist_from_library(playlist_uuid= user_playlist.UUID)
        user_playlist.saved_by = []

    save_user_manager(user_manager)

    return JSONResponse({"status": "success", "message": "Playlist updated successfully"}, status_code=200)

@playlists_router.post('/{username}/{playlist_id}/add')
def add_music_piece_to_playlist(
    username: str,
    playlist_id: str,
    mp3s: list[UploadFile] = File([]),
    uuids: str = Form('[]'),
):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=200)

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=200)

    mp3_files = mp3s
    uuid_list = json.loads(uuids)

    if len(mp3_files) != len(uuid_list): return JSONResponse({"error": "Mismatch between files and UUIDs"}, status_code=200)

    for mp3_file, mp3_uuid in zip(mp3_files, uuid_list):
        filename = secure_filename(mp3_file.filename)
        save_path = os.path.join(MP3S_DIR, filename)
        with open(save_path, "wb") as out:
            out.write(mp3_file.file.read())
        print(f"Saved: {filename} as UUID: {mp3_uuid}")

        new_music_piece = MusicPiece(
            uuid=mp3_uuid,
            name=mp3_file.filename,
            file_name=filename
        )
        user_playlist.add_music_piece(new_music_piece)
    save_user_manager(user_manager)
    return JSONResponse({"status": "success"}, status_code=200)

@playlists_router.post('/{username}/{playlist_id}/add/friends')
def add_friends_to_playlist(username: str, playlist_id: str, data: dict):

    print(f"Adding friends to playlist {playlist_id} for user {username}")

    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=404)

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=404)

    print("Adding friends to playlist: ", data.get('friends'))
    user_playlist.shared_with = data.get('friends')

    for friend in user_playlist.shared_with:
        print(f"FRIEND: {friend}")
        if friend['username'] in user_playlist.saved_by: user_playlist.saved_by.remove(friend['username'])
        user_obj = user_manager.search(friend['username'])
        if user_obj is None: return JSONResponse({"error": f"Friend {friend['username']} not found"}, status_code=404)
        user_obj.add_playlist_added_to(playlist_uuid= playlist_id, playlist_owner= username)
        print(f"{user_obj.playlists_added_to}")

    save_user_manager(user_manager)
    return JSONResponse({"status": "success", "message": "Friends added to playlist"}, status_code=200)

@playlists_router.get('/summary/{username}/{playlist_id}/music_piece/{index}/{mp3_uuid}')
def get_music_piece_summary(username: str, playlist_id: str, index: int, mp3_uuid: str):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=404)

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=404)

    if index < 0 or index >= len(user_playlist.music_pieces): return JSONResponse({"error": "Index out of range"}, status_code=400)

    music_piece = user_playlist.music_pieces[index]
    if music_piece.UUID != mp3_uuid: return JSONResponse({"error": "Music piece UUID does not match"}, status_code=400)

    return JSONResponse(music_piece.to_dict(), status_code=200)

@playlists_router.post('/update/{username}/{playlist_id}/music_piece/{index}/{mp3_uuid}')
def update_music_piece(
    username: str,
    playlist_id: str,
    index: int,
    mp3_uuid: str,
    name: str = Form(None),
    artist: str = Form(None),
    cover: UploadFile = File(None),
):
    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "User not found"}, status_code=404)

    user_playlist = user.playlists.get(playlist_id)
    if user_playlist is None: return JSONResponse({"error": "Playlist not found"}, status_code=404)

    if index < 0 or index >= len(user_playlist.music_pieces): return JSONResponse({"error": "Index out of range"}, status_code=400)

    music_piece = user_playlist.music_pieces[index]
    if music_piece.UUID != mp3_uuid: return JSONResponse({"error": "Music piece UUID does not match"}, status_code=400)

    new_name = name
    new_cover = cover
    new_artist = artist

    if new_cover:
        filename = _save_upload(new_cover, COVERS_DIR)
        music_piece.cover = f"/uploads/covers/{filename}"

    music_piece.artist = new_artist
    music_piece.name = new_name

    save_user_manager(user_manager)

    return JSONResponse({"status": "success", "message": "Music piece updated successfully", "musicPiece": music_piece.to_dict()}, status_code=200)

@playlists_router.get('/{username}/{playlist_id}/play')
@playlists_router.get('/{username}/{playlist_id}/play/{start_index}')
def play_playlist(username: str, playlist_id: str, start_index: int | None = None, shuffle: str = 'false'):
    shuffle_on = shuffle.lower() == 'true'
    print(f"Start index: {start_index}, shuffle: {shuffle_on}")

    if start_index is None: start_index = 0

    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    playlist = user.playlists.get(playlist_id)
    if not playlist:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

    if start_index < 0 or start_index >= len(playlist.music_pieces):
        return JSONResponse({"error": "Start index out of range"}, status_code=200)

    if shuffle_on: queue = randomize_playlist(playlist, start_index)
    else: queue = playlist.music_pieces

    return JSONResponse({
        'playlist':[piece.to_dict() for piece in queue],
        'startIndex': start_index,
        'currentPlaylistUUID': playlist.UUID,
        'orderedPlaylist': [piece.to_dict() for piece in playlist.music_pieces]
    }, status_code=200)

@playlists_router.get('/{username}/{playlist_id}/play-shuffled/')
def play_playlist_shuffled(username: str, playlist_id: str):
    user = user_manager.search(username)
    if not user:
        return JSONResponse({"error": "User not found"}, status_code=200)

    playlist = user.playlists.get(playlist_id)
    if not playlist:
        return JSONResponse({"error": "Playlist not found"}, status_code=200)

    randomized_starting_index = random.randint(0, len(playlist) - 1)
    queue = randomize_playlist(playlist, randomized_starting_index)

    return JSONResponse({
        'playlist': [piece.to_dict() for piece in queue],
        'startIndex': 0,
        'orderedStartingIndex': randomized_starting_index,
        'currentPlaylistUUID': playlist.UUID,
        'orderedPlaylist': [piece.to_dict() for piece in playlist.music_pieces]
    }, status_code=200)
