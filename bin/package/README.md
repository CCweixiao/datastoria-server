# DataStoria unified deployment

This archive contains the Spring Boot backend and the statically exported Next.js frontend
(`app/frontend`). The launcher starts a single Java process that serves both the web app and the
API; no Node.js runtime and no Nginx are required on the target machine — only JDK 17.

## Install and start

```bash
tar -xzf datastoria-<version>.tar.gz
cd datastoria-<version>
cp conf/datastoria.env.example conf/datastoria.env
bin/datastoria init
bin/datastoria start
bin/datastoria status
```

Open `http://<server>:8080` (the `SERVER_PORT` in `conf/datastoria.env`). Browser API requests go
to `/api/**` on the same origin; JWT auth is handled by the backend.

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

- `dev`: MySQL 5.7 with convenient local defaults (built-in dev master key and bootstrap admin).
- `prod`: MySQL 5.7 with environment-owned credentials; set the database, JWT secret and bootstrap
  admin variables documented in `conf/datastoria.env.example`. The credential master key is
  optional: unset, a random key is generated into `data/master.key` on first start — back it up.

Deployment values belong in `conf/datastoria.env`. Database upgrades always use the single packaged
MySQL Flyway migration set.

## Updating the frontend only

Rebuild the package (or run `npm run build` in `datastoria-web`) and replace the contents of
`app/frontend/` with the new `out/` directory, then restart. No jar rebuild is needed.

## Separate deployment compatibility

The backend JAR can still be deployed without the bundled frontend; it simply serves the API. For
a separately hosted frontend build, set `NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL` to the public
Java URL when running `bin/build-package.sh`; the browser then calls that origin directly (CORS
must be configured on the backend).
