"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import type { AppUIMessage, PendingActionData, ToolPart } from "@/lib/ai/ai-types";
import {
  type AskUserQuestionInput,
  type AskUserQuestionOutput,
} from "@/lib/ai/tools/server/human-interaction-types";
import { cn } from "@/lib/utils";
import { CircleAlert, HelpCircle, Loader2 } from "lucide-react";
import { memo, useMemo, useState } from "react";
import { useChatAction } from "../chat-action-context";

function previewValue(value: string) {
  const trimmed = value.trim();
  if (trimmed.length <= 120) return trimmed;
  return `${trimmed.slice(0, 117)}...`;
}

function isAskUserQuestionInput(value: unknown): value is AskUserQuestionInput {
  if (!value || typeof value !== "object") return false;

  const maybeQuestions = (value as { questions?: unknown }).questions;
  if (!Array.isArray(maybeQuestions) || maybeQuestions.length !== 1) return false;

  return maybeQuestions.every((question) => {
    if (!question || typeof question !== "object") return false;

    const header = (question as { header?: unknown }).header;
    const options = (question as { options?: unknown }).options;
    if (typeof header !== "string" || !Array.isArray(options) || options.length === 0) return false;

    return options.every((option) => {
      if (!option || typeof option !== "object") return false;

      const id = (option as { id?: unknown }).id;
      const label = (option as { label?: unknown }).label;
      const input = (option as { input?: unknown }).input;
      if (typeof id !== "string" || typeof label !== "string") return false;

      if (input === "none" || input === "text") {
        return !("choices" in option);
      }

      if (input === "select") {
        const choices = (option as { choices?: unknown }).choices;
        return (
          Array.isArray(choices) &&
          choices.length > 0 &&
          choices.every((choice) => typeof choice === "string")
        );
      }

      return false;
    });
  });
}

function normalizeAskUserQuestionInput(value: unknown): AskUserQuestionInput | undefined {
  if (isAskUserQuestionInput(value)) {
    return value;
  }
  if (!value || typeof value !== "object") {
    return undefined;
  }

  const questions = (value as { questions?: unknown }).questions;
  if (!Array.isArray(questions) || questions.length !== 1) {
    return undefined;
  }
  const question = questions[0];
  if (!question || typeof question !== "object") {
    return undefined;
  }

  // Some OpenAI-compatible providers follow the semantic tool description but emit the common
  // compact shape { question, options: string[] }. Normalize it to DataStoria's richer UI shape
  // instead of leaving a durable WAITING_INPUT run stuck on "Preparing question...".
  const header = (question as { question?: unknown }).question;
  const options = (question as { options?: unknown }).options;
  if (typeof header === "string" && header.trim().length > 0 && Array.isArray(options)) {
    if (
      options.length > 0 &&
      options.every((option) => typeof option === "string" && option.trim().length > 0)
    ) {
      return {
        questions: [
          {
            header: header.trim(),
            options: options.map((option, index) => ({
              id: `option-${index + 1}`,
              label: (option as string).trim(),
              input: "none" as const,
            })),
          },
        ],
      };
    }
  }

  const choices = (question as { choices?: unknown }).choices;
  if (
    typeof header === "string" &&
    header.trim().length > 0 &&
    Array.isArray(choices) &&
    choices.length > 0 &&
    choices.every(
      (choice) =>
        choice !== null &&
        typeof choice === "object" &&
        typeof (choice as { label?: unknown }).label === "string" &&
        ((choice as { label: string }).label as string).trim().length > 0
    )
  ) {
    return {
      questions: [
        {
          header: header.trim(),
          options: choices.map((choice, index) => ({
            id: `option-${index + 1}`,
            label: (choice as { label: string }).label.trim(),
            input: "none" as const,
          })),
        },
      ],
    };
  }

  // Object options carrying at least a label (e.g. { label, description }), emitted by providers
  // that follow the semantic tool description but omit the strict { id, label, input } contract.
  // Mirrors the labeled-choices branch but for the `options` key; each option is a discrete choice.
  if (
    typeof header === "string" &&
    header.trim().length > 0 &&
    Array.isArray(options) &&
    options.length > 0 &&
    options.every(
      (opt) =>
        opt !== null &&
        typeof opt === "object" &&
        typeof (opt as { label?: unknown }).label === "string" &&
        ((opt as { label: string }).label as string).trim().length > 0
    )
  ) {
    return {
      questions: [
        {
          header: header.trim(),
          options: options.map((opt, index) => ({
            id: `option-${index + 1}`,
            label: (opt as { label: string }).label.trim(),
            input: "none" as const,
          })),
        },
      ],
    };
  }

  return undefined;
}

function extractAskUserQuestionInput(toolPart: ToolPart): AskUserQuestionInput | undefined {
  const candidates = [
    toolPart.input,
    (toolPart as { args?: unknown }).args,
    (toolPart as { toolCall?: { input?: unknown; args?: unknown } }).toolCall?.input,
    (toolPart as { toolCall?: { input?: unknown; args?: unknown } }).toolCall?.args,
  ];

  for (const candidate of candidates) {
    const normalized = normalizeAskUserQuestionInput(candidate);
    if (normalized) {
      return normalized;
    }
  }
  return undefined;
}

export const MessageToolAskUserQuestion = memo(function MessageToolAskUserQuestion({
  part,
  pendingAction,
  isRunning = true,
}: {
  part: AppUIMessage["parts"][0];
  pendingAction?: PendingActionData;
  isRunning?: boolean;
}) {
  const { t } = useUiPreferences();
  const toolPart = part as ToolPart;
  const toolCallId =
    (toolPart as { toolCallId?: string }).toolCallId ||
    (toolPart as { id?: string }).id ||
    (toolPart as unknown as { toolCall?: { toolCallId?: string } }).toolCall?.toolCallId ||
    "";
  const input = extractAskUserQuestionInput(toolPart);
  const output = toolPart.output as AskUserQuestionOutput | undefined;
  const { onToolOutput } = useChatAction();
  const [selectedOptionId, setSelectedOptionId] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [draftValue, setDraftValue] = useState("");
  const questionKey = toolCallId || "ask-user-question";

  const question = input?.questions?.[0];
  const singleOption = question?.options.length === 1 ? question.options[0] : undefined;
  const selectedOption = useMemo(
    () =>
      singleOption
        ? singleOption
        : question?.options.find((option) => option.id === selectedOptionId),
    [question?.options, selectedOptionId, singleOption]
  );
  const shouldShowTypedInput = selectedOption?.input === "text";

  const submitAnswer = async (
    answer: AskUserQuestionOutput
  ): Promise<{ success: true } | { success: false; error: string }> => {
    if (!toolCallId) {
      return { success: false, error: t("tool.questionMissingId") };
    }
    if (!pendingAction) {
      return { success: false, error: t("tool.questionActionUnavailable") };
    }
    if (isSubmitting || hasSubmitted) {
      return { success: false, error: t("tool.submissionInProgress") };
    }
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      await onToolOutput({
        runId: pendingAction.runId,
        actionId: pendingAction.actionId,
        toolCallId,
        output: answer,
      });
      setHasSubmitted(true);
    } catch (error) {
      const message = error instanceof Error ? error.message : t("tool.submitFailed");
      setSubmitError(message);
      setIsSubmitting(false);
      return { success: false, error: message };
    }
    setIsSubmitting(false);
    return { success: true };
  };

  const handleOptionChange = (optionId: string) => {
    if (output || isSubmitting || hasSubmitted) return;
    const option = question?.options.find((item) => item.id === optionId);
    if (!option) return;
    setSubmitError(null);
    setSelectedOptionId(option.id);
    setDraftValue("");
  };

  const handleSubmitSelectedOption = async () => {
    if (!selectedOption || !question) return;

    const normalizedValue =
      selectedOption.input === "none" ? selectedOption.label : draftValue.trim();
    if (!normalizedValue) {
      setSubmitError(t("tool.enterValue"));
      return;
    }

    const result = await submitAnswer({
      optionId: selectedOption.id,
      label: selectedOption.label,
      input: selectedOption.input,
      value: normalizedValue,
    });

    if (!result.success) {
      setSubmitError(result.error);
    }
  };

  if (!question) {
    if (isRunning) {
      return (
        <div className="flex items-start gap-2">
          <Loader2 className="mt-0.5 h-4 w-4 animate-spin text-muted-foreground" />
          <div className="text-sm text-muted-foreground">{t("tool.preparingQuestion")}</div>
        </div>
      );
    }

    return (
      <div className="mt-2 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm">
        <div className="flex items-center gap-2 font-medium text-destructive">
          <CircleAlert className="h-4 w-4" />
          {t("tool.askUserQuestion")}
        </div>
        <div className="mt-2 text-muted-foreground">{t("tool.questionUnavailable")}</div>
      </div>
    );
  }

  return (
    <div className="py-1">
      <div className="flex items-start items-center gap-2">
        {output ? (
          <HelpCircle className="mt-0.5 h-3 w-3" />
        ) : isSubmitting ? (
          <Loader2 className="mt-0.5 h-3 w-3 animate-spin" />
        ) : (
          <HelpCircle className="mt-0.5 h-3 w-3 text-muted-foreground" />
        )}
        <div className="text-sm font-medium text-foreground">{question.header}</div>
      </div>
      <div className={cn("mt-1 space-y-2", output && "pl-5")}>
        {output ? (
          <div className="rounded-md border border-border/50 bg-background/70 px-3 py-2 text-sm">
            <div className="whitespace-pre-wrap break-all font-mono text-sm text-muted-foreground">
              {previewValue(output.value)}
            </div>
          </div>
        ) : (
          <>
            {!singleOption && (
              <RadioGroup
                className="flex flex-wrap gap-2"
                value={selectedOptionId}
                onValueChange={handleOptionChange}
                disabled={isSubmitting || hasSubmitted}
              >
                {question.options.map((option) => {
                  const itemId = `ask-user-question-${questionKey}-${option.id}`;
                  return (
                    <div
                      key={option.id}
                      className={cn(
                        "inline-flex items-center gap-2 bg-background/50 pl-0 pr-3 text-sm transition-colors hover:bg-background/80",
                        selectedOptionId === option.id && "bg-background",
                        (isSubmitting || hasSubmitted) && "cursor-not-allowed opacity-60"
                      )}
                    >
                      <RadioGroupItem
                        id={itemId}
                        value={option.id}
                        className="data-[state=checked]:border-transparent h-3 w-3"
                      />
                      <Label htmlFor={itemId} className="cursor-pointer text-sm font-normal">
                        {option.label}
                      </Label>
                    </div>
                  );
                })}
              </RadioGroup>
            )}

            {selectedOption && (
              <>
                {selectedOption.input === "select" ? (
                  <div className="flex flex-wrap gap-2">
                    {selectedOption.choices.map((choice) => {
                      const isSelected = draftValue === choice;
                      return (
                        <Button
                          key={choice}
                          type="button"
                          size="sm"
                          variant={isSelected ? "secondary" : "outline"}
                          className={cn("text-xs", isSelected && "ring-1 ring-ring")}
                          onClick={() => {
                            setSubmitError(null);
                            setDraftValue(choice);
                          }}
                          disabled={isSubmitting || hasSubmitted}
                        >
                          {choice}
                        </Button>
                      );
                    })}
                  </div>
                ) : shouldShowTypedInput ? (
                  <Textarea
                    className="min-h-[150px] font-mono text-xs focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-input"
                    placeholder={selectedOption.label}
                    value={draftValue}
                    onChange={(e) => {
                      setSubmitError(null);
                      setDraftValue(e.target.value);
                    }}
                    disabled={isSubmitting || hasSubmitted}
                  />
                ) : (
                  <div className="rounded-md border border-border/50 bg-background/70 px-3 py-2 text-sm">
                    {selectedOption.label}
                  </div>
                )}

                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    size="sm"
                    className="h-8"
                    onClick={() => void handleSubmitSelectedOption()}
                    disabled={isSubmitting || hasSubmitted}
                  >
                    {isSubmitting || hasSubmitted ? (
                      <>
                        {isSubmitting && <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />}
                        {isSubmitting ? t("tool.submitting") : t("tool.submitted")}
                      </>
                    ) : (
                      t("tool.submit")
                    )}
                  </Button>
                  {!singleOption && (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="h-8"
                      onClick={() => {
                        setSelectedOptionId("");
                        setDraftValue("");
                        setSubmitError(null);
                      }}
                      disabled={isSubmitting || hasSubmitted}
                    >
                      Cancel
                    </Button>
                  )}
                </div>
              </>
            )}
          </>
        )}

        {submitError && (
          <div className="text-xs text-destructive" role="alert">
            {submitError}
          </div>
        )}
      </div>
    </div>
  );
});
