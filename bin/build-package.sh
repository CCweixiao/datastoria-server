#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_VERSION="$(
  awk '
    /<artifactId>datastoria-parent<\/artifactId>/ { project = 1; next }
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

for command in java node npm pnpm tar; do
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

if [[ ! -f "$PROJECT_DIR/datastoria-web/external/number-flow/package.json" ]]; then
  command -v git >/dev/null 2>&1 || {
    echo "Frontend submodules are missing and git is unavailable." >&2
    exit 1
  }
  git -C "$PROJECT_DIR" submodule update --init --recursive
fi

echo "Building backend and datastoria-web (API base: $PUBLIC_API_BASE)..."
"$PROJECT_DIR/mvnw" -B -ntp -f "$PROJECT_DIR/pom.xml" clean package \
  -Ddatastoria.web.api-base-url="$PUBLIC_API_BASE"

BACKEND_JAR="$(
  find "$PROJECT_DIR/datastoria-boot/target" -maxdepth 1 -type f \
    -name 'datastoria-boot-*.jar' ! -name '*.original' | head -1
)"
[[ -n "$BACKEND_JAR" ]] || {
  echo "Backend executable JAR was not produced." >&2
  exit 1
}
WEB_ARCHIVE="$(
  find "$PROJECT_DIR/datastoria-web/target" -maxdepth 1 -type f \
    -name 'datastoria-web-*-standalone.tar.gz' | head -1
)"
[[ -n "$WEB_ARCHIVE" ]] || {
  echo "Frontend standalone archive was not produced." >&2
  exit 1
}

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/app/backend" "$STAGE_DIR/app/frontend" "$STAGE_DIR/bin" \
  "$STAGE_DIR/conf" "$STAGE_DIR/data" "$STAGE_DIR/logs" "$STAGE_DIR/run"

cp "$BACKEND_JAR" "$STAGE_DIR/app/backend/datastoria-server.jar"
tar -C "$STAGE_DIR/app/frontend" -xzf "$WEB_ARCHIVE"
cp "$PROJECT_DIR/bin/datastoria.sh" "$STAGE_DIR/bin/datastoria"
cp "$PROJECT_DIR/bin/package/conf/datastoria.env.example" "$STAGE_DIR/conf/datastoria.env.example"
cp "$PROJECT_DIR/bin/package/README.md" "$STAGE_DIR/README.md"
chmod +x "$STAGE_DIR/bin/datastoria"

cat >"$STAGE_DIR/conf/build.env" <<EOF
DATASTORIA_PACKAGE_VERSION=$VERSION
NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL=$PUBLIC_API_BASE
EOF

tar -C "$DIST_DIR" -czf "$ARCHIVE" "$PACKAGE_NAME"
echo "Package created: $ARCHIVE"
