// Captures a raw response from a service base URL and saves it as a capture
// JSON file that the semantic diff can consume.
//
// Usage:
//   node src/capture.js <baseUrl> <method> <path> [payloadFile] [outFile]
//
// For SSE responses (content-type text/event-stream) the full body is buffered
// and parsed into chunks via src/sse.js. Captures are desensitised by the
// caller before committing (this script writes raw bytes).
//
// Environment:
//   DS_USER_EMAIL   optional x-datastoria-user-email header value
//   DS_SHARE_CODE   optional X-Session-Share-Code header value

import { readFileSync, writeFileSync } from "node:fs";
import { exit } from "node:process";
import { parseSseChunks } from "./sse.js";

async function main() {
  const [baseUrl, method, path, payloadFile, outFile] = process.argv.slice(2);
  if (!baseUrl || !method || !path) {
    console.error("usage: node src/capture.js <baseUrl> <method> <path> [payloadFile] [outFile]");
    exit(2);
  }

  const url = `${baseUrl.replace(/\/$/, "")}${path}`;
  const headers = {
    "content-type": "application/json",
  };
  if (process.env.DS_USER_EMAIL) headers["x-datastoria-user-email"] = process.env.DS_USER_EMAIL;
  if (process.env.DS_SHARE_CODE) headers["X-Session-Share-Code"] = process.env.DS_SHARE_CODE;

  const body = payloadFile ? readFileSync(payloadFile, "utf8") : undefined;

  const res = await fetch(url, { method, headers, body });
  const contentType = res.headers.get("content-type") || "";
  const responseHeaders = Object.fromEntries(res.headers.entries());
  const text = await res.text();

  const capture = {
    url,
    method,
    status: res.status,
    headers: responseHeaders,
    contentType,
    capturedAt: new Date().toISOString(),
  };

  if (contentType.includes("text/event-stream")) {
    const { done, chunks } = parseSseChunks(text);
    capture.stream = true;
    capture.raw = text;
    capture.chunks = chunks;
    capture.done = done;
  } else {
    capture.stream = false;
    try {
      capture.body = text ? JSON.parse(text) : null;
    } catch {
      capture.body = text;
    }
  }

  const out = outFile || `capture-${Date.now()}.json`;
  writeFileSync(out, JSON.stringify(capture, null, 2));
  console.log(`captured ${res.status} ${contentType} -> ${out}`);
}

main().catch((e) => {
  console.error(e);
  exit(1);
});
