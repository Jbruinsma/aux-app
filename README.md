# Aux

Aux is a social app for finding people with similar music taste. Users select favorite artists/genres (or connect existing listening data), and the app computes a compatibility score with other users.

This project is built on top of **UnChained**, an earlier solo project for uploading, organizing, and playing MP3 collections in-browser. Aux extends that foundation with profiles, taste-matching, and a social layer.

Built for the Programming Language Concepts course project.

---

## Features

### Inherited from UnChained
- Upload and manage MP3 files
- Create, edit, and manage playlists
- In-browser playback (play, pause, skip, shuffle, back)
- Basic playlist sharing (public/private, friend permissions)

### New for Aux
- User profiles with favorite artists/genres
- Compatibility scoring between users based on shared taste
- Browse/match view showing compatibility % with other users

---

## Tech Stack

- **Backend**: Python, [FastAPI](https://fastapi.tiangolo.com/) with [Pydantic](https://docs.pydantic.dev/) for request/response validation
- **Frontend**: [Vue.js](https://vuejs.org/)
- **Database**: SQLite (migrating from the original AVL tree + in-memory persistence)
- **Migrations**: [Alembic](https://alembic.sqlalchemy.org/)

---

## Project Structure

- `backend/` → FastAPI server: API routes, data models, compatibility logic
- `frontend/` → Vue.js application: player, profiles, matching UI

---

## Local Setup

> Setup instructions will be filled in as the FastAPI/DB migration lands.

### Backend
```bash
cd backend
pip install -r requirements.txt
# run migrations, start server — TBD
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## Roadmap

- [ ] Migrate backend from Flask to FastAPI
- [ ] Replace AVL tree storage with SQLite + Alembic migrations
- [ ] User authentication
- [ ] Profile: favorite artists/genres
- [ ] Compatibility scoring algorithm
- [ ] Match/browse UI

---

## Contributing

See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for branch rules and PR workflow before making changes.

## License

This project is for educational purposes.
