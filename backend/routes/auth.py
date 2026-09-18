# LOGIN, REGISTER


from flask import Blueprint, request, jsonify

from backend.classes.user import User
from backend.classes.user_manager import save_user_manager
from backend.instances import user_manager


auth_bp = Blueprint('auth', __name__)

@auth_bp.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')

    user = user_manager.search(username)
    if not user: return jsonify({"error": "Username or password incorrect"}), 200

    correct_password = user.check_password(password)
    if not correct_password: return jsonify({"error": "Username or password incorrect"}), 200
    return jsonify({"message": "Login successful"}), 200

@auth_bp.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    if user_manager.search(username): return jsonify({"error": "Username already exists"}), 200

    new_user = User(username= username, raw_password=password)
    user_manager.insert(key=username, value=new_user)
    save_user_manager(user_manager)
    return jsonify({"message": "Registration successful"}), 200