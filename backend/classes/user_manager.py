from backend.classes.user import User
from backend.db import load_all_users, save_all_users


class UserManager:

    def __init__(self) -> None:
        self._users: dict[str, User] = {}

    def insert(self, key: str, value: User = None) -> None:
        self._users[key] = value

    def delete(self, key: str) -> None:
        self._users.pop(key, None)

    def contains_key(self, key: str) -> bool:
        return key in self._users

    def search(self, key: str) -> User | None:
        return self._users.get(key)

    def update_key(self, old_key: str, new_key: str) -> bool:
        user_data = self._users.get(old_key)
        if user_data is None:
            return False
        del self._users[old_key]
        self._users[new_key] = user_data
        return True

    def in_order_traversal(self):
        for key in sorted(self._users):
            yield key, self._users[key]


def save_user_manager(user_manager: UserManager) -> None:
    save_all_users(user_manager._users)


def load_user_manager() -> UserManager:
    manager = UserManager()
    manager._users = load_all_users()
    return manager
