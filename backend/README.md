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

## Schema

Flyway applies `src/main/resources/db/migration/V*.sql` on startup; add a new `V<n>__*.sql` for each change and never edit an applied one. A database that predates Flyway is baselined at V1.

## Server

Deployed to `150.136.105.240` (`ssh aux`, user `ubuntu`) by `.github/workflows/deploy.yml` on every push to `main` that touches `backend/**`. The workflow logs in with the `DEPLOY_SSH_KEY` repo secret.

- Files: `/opt/aux/` holds `aux.jar`, `.env` (copied by hand, mode 600), `aux.db`, and `uploads/`
- Service: systemd unit `aux` (`/etc/systemd/system/aux.service`), which sets `SPRING_DATASOURCE_URL` and `AUX_UPLOADS_DIR` to paths under `/opt/aux`
- Logs: `ssh aux journalctl -u aux -f`
- Firewall: port 5000 must be open both in iptables on the instance (saved with `netfilter-persistent`) and in the OCI security list for the instance's public subnet

## API docs

`docs/openapi.json` is the generated OpenAPI spec; open `docs/index.html` to browse it.
CI regenerates it on PRs that touch `backend/**`. To regenerate locally: `./mvnw verify`.
