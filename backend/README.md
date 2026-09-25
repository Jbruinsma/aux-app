# Aux backend (Java)

Spring Boot port of `../backend`. Same API contract (port 5000, same paths/JSON) so the Vue frontend works unchanged.
Shares `../backend/aux.db` with the Python backend: Alembic owns the schema, Hibernate only validates it (`ddl-auto=validate`).

## Requirements

A full JDK 25 (not just a JRE — `javac` must exist):

```bash
sudo apt install openjdk-25-jdk-headless
```

Maven is not needed; `./mvnw` downloads it on first run.

## Run

```bash
cd backend-java
./mvnw spring-boot:run
```

Stop the Python backend first — both use port 5000.

## Porting status

- Done: `GET /api/users/check-username/{username}` (reference pattern: entity → repository → controller), static `/uploads/**`, CORS.
- TODO: see the `// TODO` lists in `controller/*Controller.java` and the stub classes in `entity/`.
  Each new entity must match the existing table exactly or startup fails validation.
