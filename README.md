# Aux

A social app for finding people with similar music taste. Built for the Programming Language Concepts course.

- `frontend/`: Vue app ([`frontend/README.md`](./frontend/README.md))
- `backend/`: Spring Boot API

## Requirements

- [WebStorm](https://www.jetbrains.com/webstorm/) (recommended IDE; Vue support is built in)
- Node.js 22.12 or newer
- JDK 25, only if you run the backend yourself

## Run the frontend

```bash
cd frontend
npm install
npm run dev
```

By default the dev server sends `/api` and `/uploads` to the shared backend at `http://150.136.105.240:5000`, so
you don't need to run the backend. The shared server has its own database: register an account there first.

## Run the backend locally (optional)

```bash
cd backend
cp .env.example .env   # then set AUX_JWT_SECRET
./mvnw spring-boot:run # port 5000
```

To point the frontend at it, create `frontend/.env.local` with `AUX_BACKEND=local` and restart `npm run dev`.

API docs: `http://localhost:5000/swagger-ui.html`, or `http://150.136.105.240:5000/swagger-ui.html` on the shared server.

## Roadmap

- Frontend: [`frontend/ROADMAP.md`](./frontend/ROADMAP.md): prioritized tasks for the frontend team
- Backend: [`backend/BACKEND_REQUESTS.md`](./backend/BACKEND_REQUESTS.md): endpoints the frontend has asked for

## Contributing

Read [`CONTRIBUTING.md`](./CONTRIBUTING.md) before your first PR.
