import assert from "node:assert/strict";
import test from "node:test";
import { parseSse, parseSseChunks } from "../src/sse.js";

test("parses CRLF, multiline data, comments, and DONE", () => {
  const raw =
    ": keepalive\r\n\r\n" +
    "data: {\"type\":\"text-delta\",\r\n" +
    "data: \"delta\":\"hello\"}\r\n\r\n" +
    "data: [DONE]\r\n\r\n";

  assert.deepEqual(parseSse(raw), {
    done: true,
    payloads: ['{"type":"text-delta",\n"delta":"hello"}'],
  });
  assert.deepEqual(parseSseChunks(raw).chunks, [{ type: "text-delta", delta: "hello" }]);
});

test("preserves non-JSON payloads as raw values", () => {
  assert.deepEqual(parseSseChunks("data: not-json\n\n").chunks, [
    { __raw: true, value: "not-json" },
  ]);
});
