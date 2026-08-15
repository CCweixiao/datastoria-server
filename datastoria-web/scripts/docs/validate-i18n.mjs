// Validates that the English docs tree mirrors the Chinese (default-locale) tree
// and that all cross-tree image references resolve. Runs as part of docs:check.
//
// Chinese pages live at the docs root (docs/{index.md,manual/**,reference/**});
// English pages under docs/en/** (docs/en/dev/** stays English-only).
//
// Checks:
//   1. File-list parity between the two trees.
//   2. Every ../../en/manual/... asset referenced from Chinese pages exists.
//   3. Every relative .md link inside Chinese pages resolves within the zh tree.

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const docsRoot = path.join(webRoot, 'docs')

const errors = []

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === 'img' || entry === '.vitepress' || entry === 'public') continue
      out.push(...walk(full))
    } else if (entry.endsWith('.md')) {
      out.push(full)
    }
  }
  return out
}

const zhPages = [
  path.join(docsRoot, 'index.md'),
  ...walk(path.join(docsRoot, 'manual')),
  ...walk(path.join(docsRoot, 'reference')),
]
const enDir = path.join(docsRoot, 'en')
const enPages = existsSync(enDir)
  ? [path.join(enDir, 'index.md'), ...walk(path.join(enDir, 'manual')), ...walk(path.join(enDir, 'reference'))]
  : []

// 1. File-list parity.
const rel = (file) => path.relative(docsRoot, file).replace(/^en\//, '')
const zhRel = new Set(zhPages.map(rel))
const enRel = new Set(enPages.map(rel))
for (const page of zhRel) {
  if (!enRel.has(page)) errors.push(`Missing English page: en/${page}`)
}
for (const page of enRel) {
  if (!zhRel.has(page)) errors.push(`English page without Chinese source: ${page}`)
}

// 2. zh → en asset references and 3. relative md links inside the zh tree.
for (const file of zhPages) {
  const content = readFileSync(file, 'utf8')
  const dir = path.dirname(file)
  const refRe = /\]\(([^)#?]+?)(?:#[^)]*)?\)/g
  for (const match of content.matchAll(refRe)) {
    const ref = match[1]
    if (/^https?:\/\//.test(ref) || ref.startsWith('/')) continue // external or public asset
    const target = path.resolve(dir, ref)
    if (!existsSync(target)) {
      errors.push(`${path.relative(docsRoot, file)}: broken reference ${ref}`)
      continue
    }
    if (ref.endsWith('.md') && target.startsWith(enDir)) {
      errors.push(`${path.relative(docsRoot, file)}: md link into the en tree: ${ref}`)
    }
  }
}

if (errors.length > 0) {
  console.error(`i18n validation failed (${errors.length} problem(s)):`)
  for (const error of errors) console.error(`  - ${error}`)
  process.exit(1)
}
console.log(`i18n validation passed: ${enPages.length} en / ${zhPages.length} zh pages in sync.`)
