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

- **Backend**: Java 25, [Spring Boot](https://spring.io/projects/spring-boot) with Spring Data JPA and Bean Validation
- **Frontend**: [Vue.js](https://vuejs.org/)
- **Database**: SQLite
- **API docs**: OpenAPI spec generated from the code, in `backend/docs/`

---

## Project Structure

- `backend/` → Spring Boot server: controllers, entities, compatibility logic
- `frontend/` → Vue.js application: player, profiles, matching UI

---

## Local Setup

### Backend
```bash
cd backend
./mvnw spring-boot:run   # port 5000; see backend/README.md
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

By default the dev server proxies `/api` and `/uploads` to the shared backend at `http://150.136.105.240:5000`, so you don't need to run the backend yourself. API docs: `http://150.136.105.240:5000/swagger-ui.html`.

To use a backend running on your own machine instead, create `frontend/.env.local` (gitignored) and restart `npm run dev`:
```
AUX_BACKEND=local
```

---

## Shared Server

The backend runs on an Oracle Cloud instance (`150.136.105.240`, plain HTTP on port 5000). Every push to `main` that touches `backend/**` is deployed automatically by `.github/workflows/deploy.yml`: GitHub Actions builds the jar, copies it to the server, and restarts the service. Check a deploy with `gh run list --workflow Deploy`.

The server has its own database, separate from your local `aux.db`, so register an account there before testing.

### Database changes

The schema is managed by Flyway. Migrations live in `backend/src/main/resources/db/migration/` and run on startup, both locally and on the server.

- To change the schema, add a new file named `V<next number>__<description>.sql`, e.g. `V2__add_play_count.sql`.
- Never edit a migration that has already been merged; write a new one instead.
- To reset your local database, delete `backend/aux.db` and restart the backend.

---

## Roadmap

- [ ] User authentication
- [ ] Profile: favorite artists/genres
- [ ] Compatibility scoring algorithm
- [ ] Match/browse UI

---

## Contributing

See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for branch rules and PR workflow before making changes.

## License

This project is for educational purposes.
