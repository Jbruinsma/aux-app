# Aux backend

Spring Boot API on port 5000, backed by the SQLite file `aux.db` (Hibernate only validates the schema: `ddl-auto=validate`).

## Requirements

A full JDK 25 (not just a JRE — `javac` must exist):

```bash
sudo apt install openjdk-25-jdk-headless
```

Maven is not needed; `./mvnw` downloads it on first run.

## Run

```bash
cd backend
cp .env.example .env   # then set AUX_JWT_SECRET
./mvnw spring-boot:run
```

## API docs

`docs/openapi.json` is the generated OpenAPI spec; open `docs/index.html` to browse it.
CI regenerates it on PRs that touch `backend/**`. To regenerate locally: `./mvnw verify`.
