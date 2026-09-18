import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from backend.config import UPLOADS_DIR, MP3S_DIR, PFPS_DIR, COVERS_DIR

app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
from backend.routes.auth import auth_router
from backend.routes.playlists import playlists_router
from backend.routes.users import users_router

app.include_router(auth_router, prefix='/api/auth')
app.include_router(playlists_router, prefix='/api/playlists')
app.include_router(users_router, prefix='/api/users')


@app.get('/uploads/covers/{filename}')
def uploaded_cover(filename: str):
    path = os.path.join(COVERS_DIR, filename)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404)
    return FileResponse(path)

@app.get('/uploads/mp3s/{filename}')
def uploaded_mp3(filename: str):
    path = os.path.join(MP3S_DIR, filename)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404)
    return FileResponse(path)

@app.get('/uploads/pfps/{filename}')
def uploaded_pfp(filename: str):
    path = os.path.join(PFPS_DIR, filename)
    if not os.path.isfile(path):
        raise HTTPException(status_code=404)
    return FileResponse(path)

if __name__ == "__main__":
    import uvicorn
    print("Starting the FastAPI app...")
    uvicorn.run("backend.app:app", host='0.0.0.0', port=5000, reload=True)
