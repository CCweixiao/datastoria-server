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

- `dev`: MySQL 5.7 with local identity headers and convenient connection defaults.
- `prod`: MySQL 5.7 with OAuth; set the database, master-key, tenant, CORS and auth URL variables
  documented in `conf/datastoria.env.example`.

Deployment values belong in `conf/datastoria.env`. Database upgrades always use the single packaged
MySQL Flyway migration set.

## Separate deployment compatibility

The backend JAR and `app/frontend` standalone server can still be deployed independently. For a
separate frontend build, set `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL` to the public Java URL when
running `bin/build-package.sh`; the `/backend` proxy is then unused.
