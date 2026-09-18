# LOGIN, REGISTER

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from backend.classes.user import User
from backend.classes.user_manager import save_user_manager
from backend.instances import user_manager


auth_router = APIRouter()

@auth_router.post('/login')
def login(data: dict):
    username = data.get('username')
    password = data.get('password')

    user = user_manager.search(username)
    if not user: return JSONResponse({"error": "Username or password incorrect"}, status_code=200)

    correct_password = user.check_password(password)
    if not correct_password: return JSONResponse({"error": "Username or password incorrect"}, status_code=200)
    return JSONResponse({"message": "Login successful"}, status_code=200)

@auth_router.post('/register')
def register(data: dict):
    username = data.get('username')
    password = data.get('password')
    if user_manager.search(username): return JSONResponse({"error": "Username already exists"}, status_code=200)

    new_user = User(username= username, raw_password=password)
    user_manager.insert(key=username, value=new_user)
    save_user_manager(user_manager)
    return JSONResponse({"message": "Registration successful"}, status_code=200)
