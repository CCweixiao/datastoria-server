// Minimal SSE parser: converts a raw SSE byte/text buffer into the list of
// `data:` payloads. Each payload is the string between `data: ` and the blank
// line `\n\n`. Multi-line `data:` fields are concatenated with `\n` per spec.
//
// The UI Message Stream protocol (AI SDK v6) emits one JSON object per
// `data:` line, terminated by `data: [DONE]`.

/**
 * Parse raw SSE text into an array of data payload strings.
 * @param {string} raw
 * @returns {{ done: boolean, payloads: string[] }}
 */
export function parseSse(raw) {
  const payloads = [];
  let done = false;
  // Split into event blocks separated by a blank line.
  const blocks = raw.replace(/\r\n/g, "\n").split(/\n\n+/);
  for (const block of blocks) {
    if (!block.trim()) continue;
    const dataLines = [];
    for (const line of block.split("\n")) {
      if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).replace(/^ /, ""));
      }
    }
    if (dataLines.length === 0) continue;
    const payload = dataLines.join("\n");
    if (payload === "[DONE]") {
      done = true;
      continue;
    }
    payloads.push(payload);
  }
  return { done, payloads };
}

/**
 * Parse SSE payloads into typed chunk objects. Non-JSON payloads are kept as
 * raw strings with `{ __raw: true }`.
 * @param {string} raw
 * @returns {{ done: boolean, chunks: Array<object|string> }}
 */
export function parseSseChunks(raw) {
  const { done, payloads } = parseSse(raw);
  const chunks = payloads.map((p) => {
    try {
      return JSON.parse(p);
    } catch {
      return { __raw: true, value: p };
    }
  });
  return { done, chunks };
}
