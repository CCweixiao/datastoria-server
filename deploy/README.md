# DataStoria unified deployment

This archive contains the Spring Boot backend and the Next.js standalone frontend. The launcher
starts both processes; no Nginx or separately installed frontend is required.

## Install and start

```bash
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
cp conf/datastoria.env.example conf/datastoria.env
bin/datastoria init
bin/datastoria start
bin/datastoria status
```

Open `http://<server>:3000`. Browser API requests use `/backend/**`; the bundled Next.js server
forwards them to the Java process through `DATASTORIA_JAVA_INTERNAL_URL`.

Runtime commands:

```bash
bin/datastoria init
bin/datastoria start
bin/datastoria stop
bin/datastoria restart
bin/datastoria status
bin/datastoria logs 200
```

## Environments and data sources

Set `DATASTORIA_PROFILE` in `conf/datastoria.env`:

- `local`: SQLite at `./data/datastoria.db`; Flyway initializes/upgrades it automatically.
- `prod`: MySQL; set `DATASTORIA_DB_URL`, `DATASTORIA_DB_USERNAME`,
  `DATASTORIA_DB_PASSWORD`, and `DATASTORIA_MASTER_KEY`.

Profile overrides belong in `conf/application-<profile>.yaml`. The launcher passes the profile and
external `conf/` directory to Spring Boot. Database creation and upgrades always use the packaged
Flyway migrations.

## Separate deployment compatibility

The backend JAR and `app/frontend` standalone server can still be deployed independently. For a
separate frontend build, set `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL` to the public Java URL when
running `bin/build-package.sh`; the `/backend` proxy is then unused.
