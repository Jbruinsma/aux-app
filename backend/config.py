import os

BASE_DIR = os.path.dirname(os.path.dirname(__file__))

UPLOADS_DIR = os.path.join(BASE_DIR, "uploads")
COVERS_DIR = os.path.join(UPLOADS_DIR, "covers")
MP3S_DIR = os.path.join(UPLOADS_DIR, "mp3s")
PFPS_DIR = os.path.join(UPLOADS_DIR, "pfps")

os.makedirs(COVERS_DIR, exist_ok=True)
os.makedirs(MP3S_DIR, exist_ok=True)
os.makedirs(PFPS_DIR, exist_ok=True)
