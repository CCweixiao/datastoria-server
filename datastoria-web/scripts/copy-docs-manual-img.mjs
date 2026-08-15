/**
 * Copy video files from any docs/manual/.../img (latest tree and version snapshots)
 * into docs/public so VitePress includes them in the build (images use markdown and
 * are bundled; Video component uses string props so only videos need copying).
 * docs/public/manual and docs/public/vX.Y.Z/ are gitignored.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..");
const docsDir = path.join(root, "docs");

const VIDEO_EXT = new Set([".webm", ".mp4", ".mov", ".avi", ".mkv"]);

function findImgDirs(dir, acc = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === "img") {
        acc.push(full);
      } else {
        findImgDirs(full, acc);
      }
    }
  }
  return acc;
}

function copyVideosOnly(srcImgDir, destImgDir) {
  if (!fs.existsSync(srcImgDir)) return;
  const entries = fs.readdirSync(srcImgDir, { withFileTypes: true });
  const videos = entries.filter(
    (e) => e.isFile() && VIDEO_EXT.has(path.extname(e.name).toLowerCase())
  );
  if (videos.length === 0) return;
  fs.mkdirSync(destImgDir, { recursive: true });
  for (const v of videos) {
    fs.copyFileSync(path.join(srcImgDir, v.name), path.join(destImgDir, v.name));
  }
}

const imgDirs = findImgDirs(docsDir);
for (const srcImg of imgDirs) {
  // Map docs/<...>/<chapter>/img → public/<...>/<chapter>/img, where <...> is
  // manual for the latest tree or versions/<vX.Y.Z>/manual for snapshots. Skip
  // public/ itself and any zh tree.
  const chapterDir = path.dirname(path.dirname(srcImg));
  const relFromDocs = path.relative(docsDir, chapterDir);
  if (relFromDocs.startsWith("public") || relFromDocs.startsWith(".vitepress") || relFromDocs.startsWith("zh")) {
    continue;
  }
  // Snapshot trees live under versions/<v>/manual/** but their video sources point at
  // /<v>/manual/** in public — mirror that layout.
  const publicRel = relFromDocs.replace(/^versions[/\\]/, "");
  const destImg = path.join(docsDir, "public", publicRel, "img");
  copyVideosOnly(srcImg, destImg);
}
