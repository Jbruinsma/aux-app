from flask import Flask, send_from_directory
from flask_cors import CORS
import os

from backend.config import UPLOADS_DIR, MP3S_DIR, PFPS_DIR, COVERS_DIR

app = Flask(__name__)
CORS(app)

# Register blueprints
from routes.auth import auth_bp
from routes.playlists import playlists_bp
from routes.users import users_bp

app.register_blueprint(auth_bp, url_prefix='/api/auth')
app.register_blueprint(playlists_bp, url_prefix='/api/playlists')
app.register_blueprint(users_bp, url_prefix='/api/users')


@app.route('/uploads/covers/<filename>')
def uploaded_cover(filename):
    return send_from_directory(COVERS_DIR, filename)

@app.route('/uploads/mp3s/<filename>')
def uploaded_mp3(filename):
    return send_from_directory(MP3S_DIR, filename)

@app.route('/uploads/pfps/<filename>')
def uploaded_pfp(filename):
    return send_from_directory(PFPS_DIR, filename)

if __name__ == "__main__":
    print("Starting the Flask app...")
    app.run(host='0.0.0.0', port=5000, debug=True)
