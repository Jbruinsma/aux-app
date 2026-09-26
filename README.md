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
