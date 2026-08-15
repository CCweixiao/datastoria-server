// Snapshots the latest documentation tree into docs/versions/v<X.Y.Z>/ for the docs site's
// version selector. Run at release time and commit the result:
//
//   npm run docs:snapshot -- 1.2.0          # create versions/v1.2.0/
//   npm run docs:snapshot -- 1.2.0 --force  # overwrite an existing snapshot
//
// What gets frozen per snapshot: the Chinese tree (index.md, manual/ with img/,
// reference/) plus the English tree under en/, and a manifest.json holding the
// sidebar structure and date. Chinese pages keep their ../../en/manual/... image
// references — the depth matches because the snapshot root (versions/vX/) simply
// replaces the docs root. Absolute /manual/ and /en/manual/ links are rewritten
// to the versioned path.

import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { manualSidebar } from '../../docs/.vitepress/sidebar-data.mjs'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const docsRoot = path.join(webRoot, 'docs')
const versionsDir = path.join(docsRoot, 'versions')

const argIndex = process.argv.indexOf('--') >= 0 ? process.argv.indexOf('--') + 1 : 2
const rawVersion = process.argv[argIndex] ?? ''
const force = process.argv.includes('--force')

const version = rawVersion.startsWith('v') ? rawVersion : `v${rawVersion}`
if (!/^v\d+\.\d+\.\d+$/.test(version)) {
  console.error(`Invalid version "${rawVersion}". Expected a semver like 1.2.0 or v1.2.0.`)
  process.exit(1)
}

const target = path.join(versionsDir, version)
if (existsSync(target)) {
  if (!force) {
    console.error(`Snapshot ${version} already exists. Pass --force to overwrite.`)
    process.exit(1)
  }
  rmSync(target, { recursive: true })
}

// 1. Freeze the latest content trees (Chinese at the snapshot root, English in en/).
mkdirSync(target, { recursive: true })
for (const entry of ['index.md', 'manual', 'reference', 'en']) {
  const src = path.join(docsRoot, entry)
  if (!existsSync(src)) {
    console.error(`Missing ${entry} under ${docsRoot}; nothing to snapshot.`)
    process.exit(1)
  }
  cpSync(src, path.join(target, entry), { recursive: true })
}

// 2. Rewrite absolute latest-version links inside the snapshot (markdown links and
//    Video component sources). Relative links and ../../../manual image references
//    keep working because the snapshot root replaces the docs root.
rewriteTree(target, version)

// 3. Copy manual videos into public/<v>/manual for the versioned Video components.
const publicManual = path.join(docsRoot, 'public', 'manual')
if (existsSync(publicManual)) {
  cpSync(publicManual, path.join(docsRoot, 'public', version, 'manual'), { recursive: true })
}

// 4. Freeze the sidebar structure so later content changes never leak into old versions.
const manifest = {
  version,
  date: new Date().toISOString().slice(0, 10),
  sidebar: { en: manualSidebar('en'), zh: manualSidebar('zh') },
}
writeFileSync(path.join(target, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)

console.log(`Snapshot created: docs/versions/${version}`)
console.log('Commit the snapshot (docs/versions/**) to publish it.')

/**
 * Rewrites markdown files under `dir`:
 *   - absolute /manual/ and /zh/manual/ links point at the versioned path
 *   - relative Video src="./img/x.webm" points at the versioned public asset
 *     (the Video component resolves relative sources against the source file
 *     path, which inside a snapshot would hit a non-existent public/versions/…)
 */
function rewriteTree(dir, version) {
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      rewriteTree(full, version)
      continue
    }
    if (!entry.endsWith('.md')) continue
    // e.g. versions/v1.1.0/manual/04-x/file.md → v1.1.0/manual/04-x
    const base = path.dirname(path.relative(docsRoot, full)).replace(/^versions\//, '')
    let content = readFileSync(full, 'utf8')
    content = content
      .replace(/([\s(:`"])\/en\/manual\//g, `$1/en/${version}/manual/`)
      .replace(/([\s(:`"])\/manual\//g, `$1/${version}/manual/`)
      .replace(/src="\.\/img\//g, `src="/${base}/img/`)
    writeFileSync(full, content)
  }
}
