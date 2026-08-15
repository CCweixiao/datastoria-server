/**
 * @vitest-environment jsdom
 */

import type { AppUIMessage } from "@/lib/ai/ai-types";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MessageToolAskUserQuestion } from "./message-tool-ask-user-question";

const onToolOutputMock = vi.fn<(...args: unknown[]) => Promise<void>>();

vi.mock("../chat-action-context", () => ({
  useChatAction: () => ({
    onToolOutput: onToolOutputMock,
  }),
}));

function createToolPart(
  overrides: Partial<Record<string, unknown>> = {}
): AppUIMessage["parts"][0] {
  return {
    type: "dynamic-tool",
    toolName: "ask_user_question",
    toolCallId: "ask-user-question-1",
    state: "input-available",
    input: {
      questions: [
        {
          header: "What time range should I use to find slow queries in system.query_log?",
          options: [
            { id: "last_60m", label: "Last 60 minutes", input: "none" },
            { id: "last_3h", label: "Last 3 hours", input: "none" },
            { id: "custom", label: "Custom (I'll specify)", input: "text" },
          ],
        },
      ],
    },
    ...overrides,
  } as unknown as AppUIMessage["parts"][0];
}

function createSelectToolPart(
  overrides: Partial<Record<string, unknown>> = {}
): AppUIMessage["parts"][0] {
  return createToolPart({
    input: {
      questions: [
        {
          header: "Which metric should I use to find expensive queries?",
          options: [
            {
              id: "find_expensive_query",
              label: "Find expensive query",
              input: "select",
              choices: ["duration", "cpu"],
            },
          ],
        },
      ],
    },
    ...overrides,
  });
}

function clickText(container: HTMLElement, text: string) {
  const element = [...container.querySelectorAll("*")].find(
    (node) => node.textContent?.trim() === text
  );
  if (!(element instanceof HTMLElement)) {
    throw new Error(`Unable to find clickable text: ${text}`);
  }

  const target =
    element.closest("button, label, [role='radio']") ??
    [...element.querySelectorAll("button, label, [role='radio']")][0] ??
    element;

  act(() => {
    target.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

describe("MessageToolAskUserQuestion", () => {
  const pendingAction = {
    runId: "run-1",
    actionId: "question-1",
    actionType: "question" as const,
    toolCallId: "ask-user-question-1",
    toolName: "ask_user_question",
    request: {},
  };
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    (
      globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }
    ).IS_REACT_ACT_ENVIRONMENT = true;
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    onToolOutputMock.mockReset();
    onToolOutputMock.mockResolvedValue(undefined);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("renders the question when the payload arrives via tool args", () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart({
            input: undefined,
            args: {
              questions: [
                {
                  header: "What time range should I use to find slow queries in system.query_log?",
                  options: [{ id: "last_3h", label: "Last 3 hours", input: "none" }],
                },
              ],
            },
          })}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    expect(container.textContent).toContain(
      "What time range should I use to find slow queries in system.query_log?"
    );
    expect(container.textContent).not.toContain("Question unavailable.");
  });

  it("normalizes compact provider questions with string options", async () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart({
            input: {
              questions: [
                {
                  question: "下一步您想做什么？",
                  options: ["查看 Schema", "查看监控"],
                },
              ],
            },
          })}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    expect(container.textContent).toContain("下一步您想做什么？");
    clickText(container, "查看 Schema");

    await act(async () => {
      clickText(container, "Submit");
      await Promise.resolve();
    });

    expect(onToolOutputMock).toHaveBeenCalledWith({
      runId: "run-1",
      actionId: "question-1",
      toolCallId: "ask-user-question-1",
      output: {
        optionId: "option-1",
        label: "查看 Schema",
        input: "none",
        value: "查看 Schema",
      },
    });
  });

  it("normalizes provider questions with labeled choices", () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart({
            input: {
              questions: [
                {
                  question: "请选择检查方向",
                  header: "检查方向",
                  choices: [
                    { label: "Schema", description: "检查数据库 Schema" },
                    { label: "监控", description: "检查监控指标" },
                  ],
                },
              ],
            },
          })}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    expect(container.textContent).toContain("请选择检查方向");
    expect(container.textContent).toContain("Schema");
    expect(container.textContent).toContain("监控");
    expect(container.textContent).not.toContain("Question unavailable.");
  });

  it("normalizes provider questions with labeled object options", () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart({
            input: {
              questions: [
                {
                  question: "请描述用户信息表的字段设计（字段名、数据类型），以及排序键和分区键？",
                  header: "表结构设计",
                  options: [
                    { label: "我来描述字段", description: "我将提供具体的字段、类型需求" },
                    {
                      label: "请推荐一个方案",
                      description: "推荐符合 ClickHouse 最佳实践的表结构",
                    },
                  ],
                  default: "请推荐一个方案",
                },
              ],
            },
          })}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    expect(container.textContent).toContain("请描述用户信息表的字段设计");
    expect(container.textContent).toContain("我来描述字段");
    expect(container.textContent).toContain("请推荐一个方案");
    expect(container.textContent).not.toContain("Question unavailable.");
  });

  it("submits direct radio choices without requiring extra text", async () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart()}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    clickText(container, "Last 3 hours");

    expect(container.querySelector("textarea")).toBeNull();
    expect(container.textContent).toContain("Last 3 hours");

    await act(async () => {
      clickText(container, "Submit");
      await Promise.resolve();
    });

    expect(onToolOutputMock).toHaveBeenCalledWith({
      runId: "run-1",
      actionId: "question-1",
      toolCallId: "ask-user-question-1",
      output: {
        optionId: "last_3h",
        label: "Last 3 hours",
        input: "none",
        value: "Last 3 hours",
      },
    });
  });

  it("still requires typed input for custom options", async () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart()}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    clickText(container, "Custom (I'll specify)");

    expect(container.querySelector("textarea")).not.toBeNull();

    await act(async () => {
      clickText(container, "Submit");
      await Promise.resolve();
    });

    expect(onToolOutputMock).not.toHaveBeenCalled();
    expect(container.textContent).toContain("Please enter a value before submitting.");
  });

  it("submits a custom free-form answer when options do not fit", async () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createToolPart()}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    clickText(container, "Custom answer");
    const textarea = container.querySelector("textarea");
    expect(textarea instanceof HTMLTextAreaElement).toBe(true);

    if (!(textarea instanceof HTMLTextAreaElement)) {
      throw new Error("Custom answer textarea not found");
    }
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLTextAreaElement.prototype,
      "value"
    )?.set;
    act(() => {
      setter?.call(textarea, "recent 2 hours");
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
    });

    await act(async () => {
      clickText(container, "Submit");
      await Promise.resolve();
    });

    expect(onToolOutputMock).toHaveBeenCalledWith({
      runId: "run-1",
      actionId: "question-1",
      toolCallId: "ask-user-question-1",
      output: {
        optionId: "custom",
        label: "recent 2 hours",
        input: "text",
        value: "recent 2 hours",
      },
    });
  });

  it("submits the selected choice for select options", async () => {
    act(() => {
      root.render(
        <MessageToolAskUserQuestion
          part={createSelectToolPart()}
          pendingAction={pendingAction}
          isRunning={false}
        />
      );
    });

    expect(container.querySelector("textarea")).toBeNull();

    clickText(container, "duration");

    await act(async () => {
      clickText(container, "Submit");
      await Promise.resolve();
    });

    expect(onToolOutputMock).toHaveBeenCalledWith({
      runId: "run-1",
      actionId: "question-1",
      toolCallId: "ask-user-question-1",
      output: {
        optionId: "find_expensive_query",
        label: "Find expensive query",
        input: "select",
        value: "duration",
      },
    });
  });
});
