/**
 * @vitest-environment jsdom
 */

import type { AppUIMessage } from "@/lib/ai/ai-types";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MessageToolSearchFile } from "./message-tool-search-file";

function createToolPart(output: unknown): AppUIMessage["parts"][0] {
  return {
    type: "dynamic-tool",
    toolName: "search_file",
    toolCallId: "search-1",
    state: "output-available",
    input: { query: "foo" },
    output,
  } as unknown as AppUIMessage["parts"][0];
}

// The section is collapsed by default once state is "output-available"; expand it so the body is
// mounted and assertions can see the rendered output.
function expand(container: HTMLElement) {
  act(() => {
    container.querySelector("button")?.click();
  });
}

describe("MessageToolSearchFile", () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    (
      globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }
    ).IS_REACT_ACT_ENVIRONMENT = true;
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("renders the match count for structured output", () => {
    act(() => {
      root.render(
        <MessageToolSearchFile
          part={createToolPart({ matches: [{}, {}, {}], hasMore: false })}
          isRunning={false}
        />,
      );
    });
    expand(container);

    expect(container.textContent).toContain("3 matches");
  });

  it("surfaces a bare-string output as an error instead of throwing", () => {
    // Historical persisted / upstream error outputs can arrive as a bare string (e.g. an
    // AgentScope "Tool execution failed: …" message). Rendering must complete (the original bug
    // threw "Cannot use 'in' operator to search for 'matches' in <string>" during render) and the
    // error must be surfaced.
    const errorText = "Tool execution failed: Input length = 1";
    act(() => {
      root.render(
        <MessageToolSearchFile part={createToolPart(errorText)} isRunning={false} />,
      );
    });
    // Render completed past the destructuring line without throwing.
    expect(container.textContent).toContain("Search File");
    expand(container);

    expect(container.textContent).toContain(errorText);
    expect(container.innerHTML).toContain("text-destructive");
  });
});
