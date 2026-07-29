# Docker Deployment

## 1. Build the image

**From source (default):**
```bash
docker build -t datastoria -f docker/Dockerfile .
```

**With prebuilt artifacts (CI / fast):**
```bash
-- run build outside docker
npm run build

docker build -t datastoria -f docker/Dockerfile --target runner-prebuilt --build-arg BUILD_FROM_SOURCE=0 .
```

## 2. Run the container

**Basic:**

```bash
docker run --name datastoria -p 3000:3000 datastoria
```

**With environment file:**

```bash
docker run --name datastoria -p 3000:3000 --env-file .env datastoria
```

## 3. Access the application

Open [http://localhost:3000](http://localhost:3000)

## Environment Variables

Create a `.env` file in the project root. See `.env.example` for available options.
