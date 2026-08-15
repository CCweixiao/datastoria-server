// Version discovery for the docs site. Shared by .vitepress/config.mts (rewrites, nav,
// sidebars) and scripts/docs/snapshot-version.mjs (manifest writing).
//
// URL scheme: /{locale prefix}?/{vX.Y.Z/}?{page path} — locale outermost, version inside.
// Source layout: docs/versions/<v>/** (English) and docs/versions/<v>/zh/** (Chinese).
// The latest version lives at the site root (docs/index.md, docs/manual/**, docs/zh/**)
// and has no version segment in its URLs.

import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const docsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
export const versionsDir = path.join(docsRoot, 'versions')

const VERSION_RE = /^v\d+\.\d+\.\d+$/

/** Version directory names sorted newest first (semver-ish: major, minor, patch). */
export function listVersions() {
  if (!existsSync(versionsDir)) return []
  return readdirSync(versionsDir)
    .filter((name) => VERSION_RE.test(name) && existsSync(path.join(versionsDir, name, 'manifest.json')))
    .sort(compareVersions)
}

function compareVersions(a, b) {
  const pa = a.slice(1).split('.').map(Number)
  const pb = b.slice(1).split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pb[i] - pa[i]
  }
  return 0
}

/** Reads a version snapshot manifest, or null when absent/corrupt. */
export function loadManifest(version) {
  const file = path.join(versionsDir, version, 'manifest.json')
  if (!existsSync(file)) return null
  try {
    return JSON.parse(readFileSync(file, 'utf8'))
  } catch {
    return null
  }
}

/**
 * Rewrites hook target for VitePress: maps source ids under versions/ to their
 * public path. Targets KEEP the .md extension — VitePress resolves relative
 * links against the rewritten path and its inverse rewrite map is keyed with
 * .md, so stripped targets break both the build and dead-link checks.
 */
export function rewriteDest(id) {
  const m = /^versions\/(v\d+\.\d+\.\d+)\/(.+)$/.exec(id)
  if (!m) return undefined
  const [, version, rest] = m
  if (rest.startsWith('zh/')) {
    return `zh/${version}/${rest.slice(3)}`
  }
  return `${version}/${rest}`
}

/**
 * Maps a source file id (relative to srcDir, e.g. `manual/intro.md`,
 * `zh/manual/intro.md`, `versions/v1.1.0/zh/index.md`) to its public URL path
 * (without base, without extension; index pages collapse to the directory
 * URL). Returns undefined for ids that keep their natural path.
 */
export function srcToUrl(id) {
  const dest = rewriteDest(id)
  if (dest === undefined) return undefined
  return dest.replace(/\.md$/, '').replace(/(^|\/)index$/, '$1')
}

/**
 * Inverse of srcToUrl for page-existence checks: URL path (with leading slash,
 * no extension, no locale/version prefixes yet applied) → source id.
 */
export function urlToSrc(urlPath, { locale = 'en', version = null } = {}) {
  const clean = urlPath.replace(/^\/+|\/+$/g, '')
  const localePrefix = locale === 'zh' ? 'zh/' : ''
  const versionPrefix = version ? `${version}/` : ''
  return `${version ? `versions/${version}/` : ''}${localePrefix}${clean || 'index'}`
}

/** Locale prefix for nav/sidebar link building. */
export function localePrefix(locale, version = null) {
  const l = locale === 'zh' ? 'zh/' : ''
  const v = version ? `${version}/` : ''
  return `/${l}${v}`
}
