from backend.classes.playlist import Playlist
from backend.classes.user import User
from backend.classes.user_manager import UserManager, save_user_manager, load_user_manager


user_manager = load_user_manager()
def initialize_user_manager():
    save_user_manager(user_manager)

if __name__ == "__main__":
    initialize_user_manager()