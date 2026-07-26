#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_VERSION="$(
  awk '
    /<artifactId>datastoria-server<\/artifactId>/ { project = 1; next }
    project && /<version>/ {
      value = $0
      sub(/^.*<version>/, "", value)
      sub(/<\/version>.*$/, "", value)
      print value
      exit
    }
  ' "$PROJECT_DIR/pom.xml"
)"
VERSION="${DATASTORIA_PACKAGE_VERSION:-$PROJECT_VERSION}"
PACKAGE_NAME="datastoria-${VERSION}"
DIST_DIR="$PROJECT_DIR/target/dist"
STAGE_DIR="$DIST_DIR/$PACKAGE_NAME"
ARCHIVE="$DIST_DIR/$PACKAGE_NAME.tar.gz"
PUBLIC_API_BASE="${NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL:-/backend}"

for command in java node npm tar; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command not found: $command" >&2
    exit 1
  }
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[".]' '/version/ {print ($2 == "1" ? $3 : $2); exit}')"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "JDK 17 is required to build DataStoria (found: $JAVA_MAJOR)." >&2
  exit 1
fi

echo "Building Java backend..."
"$PROJECT_DIR/mvnw" -B -ntp -f "$PROJECT_DIR/pom.xml" clean package

echo "Building Next.js standalone frontend (API base: $PUBLIC_API_BASE)..."
if [[ ! -f "$PROJECT_DIR/frontend/external/number-flow/package.json" ]]; then
  command -v git >/dev/null 2>&1 || {
    echo "Frontend submodules are missing and git is unavailable." >&2
    exit 1
  }
  git -C "$PROJECT_DIR" submodule update --init --recursive
fi
(
  cd "$PROJECT_DIR/frontend"
  if [[ ! -d node_modules ]]; then
    npm ci
  fi
  NEXT_PUBLIC_DATASTORIA_SESSION_BACKEND=java \
    NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL="$PUBLIC_API_BASE" \
    npm run build
)

BACKEND_JAR="$(find "$PROJECT_DIR/target" -maxdepth 1 -type f -name 'datastoria-server-*.jar' ! -name '*.original' | head -1)"
[[ -n "$BACKEND_JAR" ]] || {
  echo "Backend executable JAR was not produced." >&2
  exit 1
}

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/app/backend" "$STAGE_DIR/app/frontend" "$STAGE_DIR/bin" \
  "$STAGE_DIR/conf" "$STAGE_DIR/data" "$STAGE_DIR/logs" "$STAGE_DIR/run"

cp "$BACKEND_JAR" "$STAGE_DIR/app/backend/datastoria-server.jar"
cp -R "$PROJECT_DIR/frontend/.next/standalone/." "$STAGE_DIR/app/frontend/"
mkdir -p "$STAGE_DIR/app/frontend/.next"
cp -R "$PROJECT_DIR/frontend/.next/static" "$STAGE_DIR/app/frontend/.next/static"
if [[ -d "$PROJECT_DIR/frontend/public" ]]; then
  cp -R "$PROJECT_DIR/frontend/public" "$STAGE_DIR/app/frontend/public"
fi
cp "$PROJECT_DIR/bin/datastoria.sh" "$STAGE_DIR/bin/datastoria"
cp "$PROJECT_DIR/deploy/conf/datastoria.env.example" "$STAGE_DIR/conf/datastoria.env.example"
cp "$PROJECT_DIR/deploy/conf/application-local.yaml" "$STAGE_DIR/conf/application-local.yaml"
cp "$PROJECT_DIR/deploy/conf/application-prod.yaml" "$STAGE_DIR/conf/application-prod.yaml"
cp "$PROJECT_DIR/deploy/README.md" "$STAGE_DIR/README.md"
chmod +x "$STAGE_DIR/bin/datastoria"

cat >"$STAGE_DIR/conf/build.env" <<EOF
DATASTORIA_PACKAGE_VERSION=$VERSION
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=$PUBLIC_API_BASE
EOF

tar -C "$DIST_DIR" -czf "$ARCHIVE" "$PACKAGE_NAME"
echo "Package created: $ARCHIVE"
