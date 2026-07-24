// Semantic diff engine for the DataStoria migration contract runner.
//
// Two modes:
//   1. JSON diff  — deep compare of JSON bodies with ignore rules.
//   2. Stream diff — compare two UI Message Stream chunk arrays by event type
//      sequence and required-field presence, ignoring random IDs, timestamps
//      and token counts.
//
// The rules mirror docs/api/stream-protocol.md §6. Anything documented as
// "must not be ignored" produces a diff entry when missing or out of order.

import { readFileSync } from "node:fs";
import { exit } from "node:process";

// ---------------------------------------------------------------------------
// Configurable ignore rules
// ---------------------------------------------------------------------------

/** Fields whose value is ignored (presence is still checked where required). */
const IGNORED_VALUES = new Set([
  "messageId",
  "toolCallId",
  "id",
  "sourceId",
  "approvalId",
]);

/** Fields that are ignored entirely (value and presence). */
const IGNORED_FIELDS = new Set(["createdAt", "updatedAt", "occurredAt"]);

/** Path fragments (dotted) treated as opaque values. */
const OPAQUE_PATHS = new Set([
  "messageMetadata.usage.promptTokens",
  "messageMetadata.usage.completionTokens",
  "messageMetadata.usage.totalTokens",
  "messageMetadata.title",
]);

/**
 * @param {unknown} a
 * @param {unknown} b
 * @param {string} path
 * @returns {string[]} list of differences (empty when equal)
 */
export function jsonDiff(a, b, path = "$") {
  if (IGNORED_FIELDS.has(path.split(".").pop())) return [];

  if (typeof a !== typeof b) {
    return [`${path}: type ${typeof a} !== ${typeof b}`];
  }
  if (a === null || b === null) {
    return a === b ? [] : [`${path}: null mismatch`];
  }
  if (typeof a !== "object") {
    return Object.is(a, b) ? [] : [`${path}: ${JSON.stringify(a)} !== ${JSON.stringify(b)}`];
  }
  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b)) {
      return [`${path}: array/non-array mismatch`];
    }
    if (a.length !== b.length) {
      return [`${path}: array length ${a.length} !== ${b.length}`];
    }
    const diffs = [];
    for (let i = 0; i < a.length; i++) {
      diffs.push(...jsonDiff(a[i], b[i], `${path}[${i}]`));
    }
    return diffs;
  }

  const diffs = [];
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  const allKeys = new Set([...keysA, ...keysB]);

  for (const key of allKeys) {
    const p = `${path}.${key}`;
    if (IGNORED_FIELDS.has(key)) continue;
    const hasA = key in a;
    const hasB = key in b;
    if (!hasA || !hasB) {
      // Field presence changes are never ignored.
      diffs.push(`${p}: present=${hasA} vs present=${hasB}`);
      continue;
    }
    if (IGNORED_VALUES.has(key) || OPAQUE_PATHS.has(p)) {
      // Value ignored; only require both present (already checked).
      continue;
    }
    diffs.push(...jsonDiff(a[key], b[key], p));
  }
  return diffs;
}

// ---------------------------------------------------------------------------
// Stream (UI Message Stream) diff
// ---------------------------------------------------------------------------

/** Merge consecutive text-delta / reasoning-delta by id, compare final text. */
function foldDeltas(chunks, type) {
  const byId = new Map();
  const order = [];
  for (const c of chunks) {
    if (c.type !== `${type}-delta`) continue;
    const id = c.id;
    if (!byId.has(id)) {
      byId.set(id, "");
      order.push(id);
    }
    byId.set(id, byId.get(id) + (c.delta ?? ""));
  }
  return order.map((id) => ({ id, text: byId.get(id) }));
}

/**
 * Compare two UI Message Stream chunk sequences.
 * @param {Array<object>} node
 * @param {Array<object>} java
 * @returns {{ diffs: string[], summary: object }}
 */
export function streamDiff(node, java) {
  const diffs = [];
  const nodeTypes = node.map((c) => c.type);
  const javaTypes = java.map((c) => c.type);

  // 1. Event type multiset equality (presence).
  const nodeCount = countBy(nodeTypes);
  const javaCount = countBy(javaTypes);
  const allTypes = new Set([...nodeCount.keys(), ...javaCount.keys()]);
  for (const t of allTypes) {
    const n = nodeCount.get(t) ?? 0;
    const j = javaCount.get(t) ?? 0;
    if (n !== j) {
      diffs.push(`event type "${t}" count: node=${n} java=${j}`);
    }
  }

  // 2. Ordering invariant checks (must-not-ignore ordering rules).
  const orderInvariants = [
    ["start", "text-start"],
    ["text-start", "text-delta"],
    ["text-delta", "text-end"],
    ["tool-input-start", "tool-input-available"],
    ["tool-input-available", "tool-output-available"],
    ["start", "finish"],
  ];
  for (const [before, after] of orderInvariants) {
    const nd = firstIndexOfPair(nodeTypes, before, after);
    const jd = firstIndexOfPair(javaTypes, before, after);
    // Invariant does not apply when neither side has the event pair.
    if (nd.status === "missing" && jd.status === "missing") continue;
    // Both sides have the pair in correct order — nothing to flag.
    if (nd.status === "ok" && jd.status === "ok") continue;
    diffs.push(
      `ordering invariant ${before}<-${after}: node=${describe(nd)} java=${describe(jd)}`,
    );
  }

  // 3. finish chunk must exist with the same finishReason presence flag.
  const nodeFinish = node.find((c) => c.type === "finish");
  const javaFinish = java.find((c) => c.type === "finish");
  if (!!nodeFinish !== !!javaFinish) {
    diffs.push("finish chunk presence mismatch");
  } else if (nodeFinish && javaFinish) {
    if ((nodeFinish.finishReason ?? "stop") !== (javaFinish.finishReason ?? "stop")) {
      diffs.push(
        `finishReason: node=${nodeFinish.finishReason} java=${javaFinish.finishReason}`,
      );
    }
  }

  // 4. Folded delta text must match.
  for (const kind of ["text", "reasoning"]) {
    const nd = foldDeltas(node, kind);
    const jd = foldDeltas(java, kind);
    if (nd.length !== jd.length) {
      diffs.push(`${kind}-delta group count: node=${nd.length} java=${jd.length}`);
      continue;
    }
    for (let i = 0; i < nd.length; i++) {
      if (nd[i].text !== jd[i].text) {
        diffs.push(`${kind} folded text #${i}: node=${JSON.stringify(nd[i].text)} java=${JSON.stringify(jd[i].text)}`);
      }
    }
  }

  // 5. toolName sequence must match (tool input/output identity).
  const nodeTools = node
    .filter((c) => c.type === "tool-input-available")
    .map((c) => c.toolName);
  const javaTools = java
    .filter((c) => c.type === "tool-input-available")
    .map((c) => c.toolName);
  if (JSON.stringify(nodeTools) !== JSON.stringify(javaTools)) {
    diffs.push(`toolName sequence: node=${JSON.stringify(nodeTools)} java=${JSON.stringify(javaTools)}`);
  }

  return {
    diffs,
    summary: {
      nodeEvents: nodeTypes,
      javaEvents: javaTypes,
      nodeDone: node.some((c) => c.__done),
      javaDone: java.some((c) => c.__done),
    },
  };
}

function countBy(arr) {
  const m = new Map();
  for (const v of arr) m.set(v, (m.get(v) ?? 0) + 1);
  return m;
}

/** Returns {status: 'ok'|'missing'|'order', nodeIdx, javaIdx} for a before<-after pair. */
function firstIndexOfPair(types, before, after) {
  const bi = types.indexOf(before);
  const ai = types.indexOf(after);
  if (bi === -1 || ai === -1) return { status: "missing", bi, ai };
  if (bi >= ai) return { status: "order", bi, ai };
  return { status: "ok", bi, ai };
}

function describe(r) {
  if (r.status === "ok") return "ok";
  if (r.status === "missing") return `missing(${r.bi},${r.ai})`;
  return `order(${r.bi}>=${r.ai})`;
}

// ---------------------------------------------------------------------------
// CLI: node src/semantic-diff.js <captureA.json> <captureB.json>
// ---------------------------------------------------------------------------

function runCli() {
  const [aPath, bPath] = process.argv.slice(2);
  if (!aPath || !bPath) {
    console.error("usage: node src/semantic-diff.js <capture-a.json> <capture-b.json>");
    exit(2);
  }
  const a = JSON.parse(readFileSync(aPath, "utf8"));
  const b = JSON.parse(readFileSync(bPath, "utf8"));

  let diffs = [];
  if (a.stream && b.stream) {
    const result = streamDiff(a.chunks ?? [], b.chunks ?? []);
    diffs = result.diffs;
    console.log(JSON.stringify(result.summary, null, 2));
  } else {
    diffs = jsonDiff(a.body ?? a, b.body ?? b);
  }

  if (a.status && b.status && a.status !== b.status) {
    diffs.unshift(`status: ${a.status} !== ${b.status}`);
  }

  if (diffs.length === 0) {
    console.log("SEMANTIC MATCH");
    exit(0);
  } else {
    console.error(`SEMANTIC DIFF (${diffs.length}):`);
    for (const d of diffs) console.error(`  - ${d}`);
    exit(1);
  }
}

// Run only when invoked directly.
if (import.meta.url === `file://${process.argv[1]}`) {
  runCli();
}
