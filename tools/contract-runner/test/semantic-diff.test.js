import assert from "node:assert/strict";
import test from "node:test";
import { captureDiff, jsonDiff, streamDiff } from "../src/semantic-diff.js";

const streamHeaders = {
  "content-type": "text/event-stream",
  "cache-control": "no-cache",
  "x-vercel-ai-ui-message-stream": "v1",
};

test("ignores token values but not token field presence", () => {
  const left = { messageMetadata: { usage: { promptTokens: 10 } } };
  const right = { messageMetadata: { usage: { promptTokens: 99 } } };
  assert.deepEqual(jsonDiff(left, right), []);
  assert.notDeepEqual(jsonDiff(left, { messageMetadata: { usage: {} } }), []);
});

test("allows different delta chunking when folded text matches", () => {
  const node = [
    { type: "start" },
    { type: "text-start", id: "node-id" },
    { type: "text-delta", id: "node-id", delta: "hel" },
    { type: "text-delta", id: "node-id", delta: "lo" },
    { type: "text-end", id: "node-id" },
    { type: "finish", finishReason: "stop" },
  ];
  const java = [
    { type: "start" },
    { type: "text-start", id: "java-id" },
    { type: "text-delta", id: "java-id", delta: "hello" },
    { type: "text-end", id: "java-id" },
    { type: "finish", finishReason: "stop" },
  ];
  assert.deepEqual(streamDiff(node, java).diffs, []);
});

test("detects changed tool input and output", () => {
  const node = [
    { type: "tool-input-available", toolCallId: "a", toolName: "get_tables", input: { limit: 5 } },
    { type: "tool-output-available", toolCallId: "a", output: [{ table: "events" }] },
  ];
  const java = [
    { type: "tool-input-available", toolCallId: "b", toolName: "get_tables", input: { limit: 10 } },
    { type: "tool-output-available", toolCallId: "b", output: [{ table: "users" }] },
  ];
  assert.ok(streamDiff(node, java).diffs.some((diff) => diff.includes("toolInputs")));
  assert.ok(streamDiff(node, java).diffs.some((diff) => diff.includes("toolOutputs")));
});

test("detects missing DONE and required stream headers", () => {
  const base = {
    status: 200,
    stream: true,
    done: true,
    headers: streamHeaders,
    chunks: [{ type: "finish", finishReason: "stop" }],
  };
  const changed = {
    ...base,
    done: false,
    headers: { ...streamHeaders, "x-vercel-ai-ui-message-stream": "v2" },
  };
  const diffs = captureDiff(base, changed).diffs;
  assert.ok(diffs.some((diff) => diff.includes("[DONE]")));
  assert.ok(diffs.some((diff) => diff.includes("x-vercel-ai-ui-message-stream")));
});
