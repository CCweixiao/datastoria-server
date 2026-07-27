# DataStoria Frontend

Next.js 16 + React 19 management console for DataStoria. The frontend is not a standalone backend:
connections, credentials, models, sessions and Agent runs are owned by the Spring Boot service.

## Development

From the repository root, start Java with the `local` profile. Then:

```bash
git submodule update --init --recursive
cd frontend
npm install
NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=http://127.0.0.1:8080 \
NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL=dev@example.com \
npm run dev
```

Open `http://localhost:3000`.

## Checks

```bash
npm run format:check
npm run typecheck
npm run lint
npm test -- --run
npm run build
```

## Security boundary

- Never add API keys or database passwords to `NEXT_PUBLIC_*` variables.
- The browser references saved connection/provider IDs; Java injects encrypted credentials.
- `/backend/**` is the same-origin proxy used by the unified package.
- Client Tool execution is not a trusted boundary; approve/deny/respond use Java Action APIs.

Project documentation: [../docs/README.md](../docs/README.md).
