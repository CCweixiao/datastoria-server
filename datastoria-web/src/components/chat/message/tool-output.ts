/**
 * Resolves a tool part's `output` into a form that is always safe to destructure as an object.
 *
 * The backend guarantees tool output is structured (object or array) going forward, but historical
 * persisted messages and upstream error paths can still deliver a bare string (e.g. an AgentScope
 * "Tool execution failed: …" message). Using the `in` operator or property access on such a value
 * throws and crashes the chat. This helper normalises those cases:
 *
 * - `null`/`undefined` (tool still running) → `undefined`
 * - a plain object → returned unchanged (typed as `T`)
 * - anything else (string, array, number, …) → `{ error: stringified }`
 *
 * Object-expecting renderers can then read `.error` safely. Array-expecting renderers must still
 * guard with `Array.isArray(...)` — a failed tool surfaces `{ error }`, not an array.
 */
export type ToolOutputError = { error: string };

export function readToolOutputObject<T extends object = Record<string, unknown>>(
  output: unknown,
): T | ToolOutputError | undefined {
  if (output == null) {
    return undefined;
  }
  if (typeof output === "object" && !Array.isArray(output)) {
    return output as T;
  }
  return { error: toErrorMessage(output) };
}

function toErrorMessage(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  try {
    return String(value);
  } catch {
    return "Tool output could not be displayed.";
  }
}
