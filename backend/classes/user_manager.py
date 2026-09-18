from backend.classes.user import User


class AVLNode:
    def __init__(self, key : str, value : User = None) -> None:
        self.key : str = key
        self.value : User = value
        self.left : AVLNode | None = None
        self.right : AVLNode | None = None
        self.height : int = 1

    def __str__(self) -> str:
        return f'AVLNode(key={self.key}, value={self.value}, height={self.height})'

class UserManager:

    def __init__(self) -> None:
        self.root = None

    @staticmethod
    def get_height(node : AVLNode) -> int:
        return node.height if node else 0

    def get_balance(self, node : AVLNode) -> int:
        return self.get_height(node.left) - self.get_height(node.right) if node else 0

    def right_rotate(self, y : AVLNode) -> AVLNode:
        x = y.left
        T2 = x.right

        # Perform rotation
        x.right = y
        y.left = T2

        # Update heights
        y.height = 1 + max(self.get_height(y.left), self.get_height(y.right))
        x.height = 1 + max(self.get_height(x.left), self.get_height(x.right))

        return x

    def left_rotate(self, x : AVLNode) -> AVLNode:
        y = x.right
        T2 = y.left

        # Perform rotation
        y.left = x
        x.right = T2

        # Update heights
        x.height = 1 + max(self.get_height(x.left), self.get_height(x.right))
        y.height = 1 + max(self.get_height(y.left), self.get_height(y.right))

        return y

    def insert(self, key : str, value : User = None) -> None:
        self.root = self._insert(self.root, key, value)

    def _insert(self, node : AVLNode, key : str, value : User) -> AVLNode:
        if not node:
            return AVLNode(key, value)
        if key < node.key:
            node.left = self._insert(node.left, key, value)
        elif key > node.key:
            node.right = self._insert(node.right, key, value)
        else:
            node.value = value
            return node

        # Update height and rebalance
        node.height = 1 + max(self.get_height(node.left), self.get_height(node.right))
        balance = self.get_balance(node)

        # Left Left
        if balance > 1 and key < node.left.key:
            return self.right_rotate(node)
        # Right Right
        if balance < -1 and key > node.right.key:
            return self.left_rotate(node)
        # Left Right
        if balance > 1 and key > node.left.key:
            node.left = self.left_rotate(node.left)
            return self.right_rotate(node)
        # Right Left
        if balance < -1 and key < node.right.key:
            node.right = self.right_rotate(node.right)
            return self.left_rotate(node)

        return node

    @staticmethod
    def min_value_node(node : AVLNode) -> AVLNode:
        current = node
        while current.left:
            current = current.left
        return current

    def delete(self, key : str) -> None:
        self.root = self._delete(self.root, key)

    def _delete(self, node : AVLNode, key : str) -> AVLNode:
        if not node:
            return node
        if key < node.key:
            node.left = self._delete(node.left, key)
        elif key > node.key:
            node.right = self._delete(node.right, key)
        else:
            # Node with one or no child
            if not node.left:
                return node.right
            elif not node.right:
                return node.left
            # Node with two children
            temp = self.min_value_node(node.right)
            node.key = temp.key
            node.value = temp.value
            node.right = self._delete(node.right, temp.key)

        if not node:
            return node

        # Update height and rebalance
        node.height = 1 + max(self.get_height(node.left), self.get_height(node.right))
        balance = self.get_balance(node)

        # Left Left
        if balance > 1 and self.get_balance(node.left) >= 0:
            return self.right_rotate(node)
        # Left Right
        if balance > 1 and self.get_balance(node.left) < 0:
            node.left = self.left_rotate(node.left)
            return self.right_rotate(node)
        # Right Right
        if balance < -1 and self.get_balance(node.right) <= 0:
            return self.left_rotate(node)
        # Right Left
        if balance < -1 and self.get_balance(node.right) > 0:
            node.right = self.right_rotate(node.right)
            return self.left_rotate(node)

        return node

    def contains_key(self, key : str) -> bool:
        return self._search(self.root, key) is not None

    def search(self, key : str) -> User | None:
        return self._search(self.root, key)

    def _search(self, node : AVLNode, key : str) -> User | None:
        if not node:
            return None
        if key == node.key:
            return node.value
        if key < node.key:
            return self._search(node.left, key)
        return self._search(node.right, key)

    def update_key(self, old_key : str, new_key : str) -> bool:
        user_data = self.search(old_key)
        if user_data is None:
            return False
        self.delete(old_key)
        print(user_data)
        print(new_key)
        self.insert(new_key, user_data)
        return True

    def in_order_traversal(self):
        yield from self._in_order(self.root)

    def _in_order(self, node):
        if node is None:
            return
        # traverse left subtree
        yield from self._in_order(node.left)
        # visit this node
        yield node.key, node.value
        # traverse right subtree
        yield from self._in_order(node.right)

import pickle

PICKLE_FILE = r"C:\Users\justi\Desktop\currentProjects\Unchained\backend\users.pkl"

def save_user_manager(user_manager, filename=PICKLE_FILE):
    with open(filename, "wb") as f:
        pickle.dump(user_manager, f)

def load_user_manager(filename=PICKLE_FILE):
    try:
        with open(filename, "rb") as f:
            return pickle.load(f)
    except FileNotFoundError:
        return UserManager()
