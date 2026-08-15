"use client";

import { useUiPreferences } from "@/components/shared/ui-preferences-provider";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import type { AppUIMessage, PendingActionData, ToolPart } from "@/lib/ai/ai-types";
import {
  type AskUserQuestionInput,
  type AskUserQuestionOption,
  type AskUserQuestionOutput,
} from "@/lib/ai/tools/server/human-interaction-types";
import { cn } from "@/lib/utils";
import { Check, CircleAlert, HelpCircle, Loader2, PenLine } from "lucide-react";
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
          options: choices.map((choice, index) => {
            const labeled = choice as { label: string; description?: unknown };
            return {
              id: `option-${index + 1}`,
              label: labeled.label.trim(),
              ...(typeof labeled.description === "string"
                ? { description: labeled.description }
                : {}),
              input: "none" as const,
            };
          }),
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
          options: options.map((opt, index) => {
            const labeled = opt as { label: string; description?: unknown };
            return {
              id: `option-${index + 1}`,
              label: labeled.label.trim(),
              ...(typeof labeled.description === "string"
                ? { description: labeled.description }
                : {}),
              input: "none" as const,
            };
          }),
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
  const [customActive, setCustomActive] = useState(false);
  const [customValue, setCustomValue] = useState("");
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
  // Options carrying descriptions render as a vertical choice list so the secondary line has
  // room; description-less options stay compact pills.
  const useChoiceCards = useMemo(
    () => !!question?.options.some((option) => option.description),
    [question?.options]
  );
  const isLocked = !!output || isSubmitting || hasSubmitted;

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
    if (isLocked) return;
    const option = question?.options.find((item) => item.id === optionId);
    if (!option) return;
    setSubmitError(null);
    setSelectedOptionId(option.id);
    setDraftValue("");
    setCustomActive(false);
    setCustomValue("");
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

  const handleSubmitCustomAnswer = async () => {
    const value = customValue.trim();
    if (!value) {
      setSubmitError(t("tool.enterValue"));
      return;
    }
    const result = await submitAnswer({
      optionId: "custom",
      label: value,
      input: "text",
      value,
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
    <div
      className={cn(
        "my-2 overflow-hidden rounded-xl border shadow-sm transition-colors",
        output || hasSubmitted
          ? "border-emerald-500/30 bg-emerald-500/[0.04] dark:border-emerald-500/25"
          : "border-primary/25 bg-primary/[0.03] dark:border-primary/25"
      )}
    >
      <div className="flex flex-col gap-1 px-4 pt-3">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "flex h-6 w-6 shrink-0 items-center justify-center rounded-full",
              output || hasSubmitted
                ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
                : "bg-primary/10 text-primary"
            )}
          >
            {output || hasSubmitted ? (
              <Check className="h-3.5 w-3.5" />
            ) : isSubmitting ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <HelpCircle className="h-3.5 w-3.5" />
            )}
          </span>
          <div className="text-sm font-medium text-foreground">{question.header}</div>
          {!(output || hasSubmitted) && (
            <span className="ml-auto shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
              {t("tool.askUserQuestion")}
            </span>
          )}
        </div>
        {question.description && (
          <div className="pl-8 text-xs leading-relaxed text-muted-foreground">
            {question.description}
          </div>
        )}
      </div>

      <div className="space-y-2.5 px-4 pb-3.5 pt-2.5">
        {output || hasSubmitted ? (
          <div className="ml-8 space-y-1.5">
            {(output || hasSubmitted) && (
              <div className="text-[11px] font-medium uppercase tracking-wide text-emerald-600 dark:text-emerald-400">
                {t("tool.answered")}
              </div>
            )}
            {output && (
              <div className="rounded-lg border border-border/60 bg-background/80 px-3 py-2">
                <div className="whitespace-pre-wrap break-all font-mono text-sm text-muted-foreground">
                  {previewValue(output.value)}
                </div>
              </div>
            )}
          </div>
        ) : (
          <>
            {!singleOption && (
              <RadioGroup
                className={cn("gap-2", useChoiceCards ? "flex flex-col" : "flex flex-wrap")}
                value={selectedOptionId}
                onValueChange={handleOptionChange}
                disabled={isSubmitting || hasSubmitted}
              >
                {question.options.map((option) => {
                  const itemId = `ask-user-question-${questionKey}-${option.id}`;
                  const isSelected = selectedOptionId === option.id;
                  return (
                    <div
                      key={option.id}
                      className={cn(
                        "relative transition-all",
                        useChoiceCards
                          ? cn(
                              "rounded-lg border px-3 py-2.5 transition-colors",
                              isSelected
                                ? "border-primary/50 bg-primary/[0.07] shadow-xs"
                                : "border-border/70 bg-background/60 hover:border-primary/30 hover:bg-background"
                            )
                          : cn(
                              "inline-flex items-center gap-2 rounded-full border bg-background/60 px-3 py-1.5 text-sm transition-colors",
                              isSelected
                                ? "border-primary/50 bg-primary/[0.08]"
                                : "border-border/70 hover:border-primary/30 hover:bg-background/90"
                            ),
                        (isSubmitting || hasSubmitted) && "cursor-not-allowed opacity-60"
                      )}
                    >
                      <RadioGroupItem id={itemId} value={option.id} className="h-3 w-3" />
                      <Label
                        htmlFor={itemId}
                        className={cn(
                          "cursor-pointer text-sm",
                          useChoiceCards ? "font-medium" : "font-normal"
                        )}
                      >
                        {option.label}
                      </Label>
                      {useChoiceCards && option.description && (
                        <div className="pl-5 text-xs leading-relaxed text-muted-foreground">
                          {option.description}
                        </div>
                      )}
                      {useChoiceCards && isSelected && (
                        <Check className="absolute right-3 top-3 h-4 w-4 text-primary" />
                      )}
                    </div>
                  );
                })}
              </RadioGroup>
            )}

            {selectedOption && !customActive && (
              <SelectedOptionEditor
                option={selectedOption}
                draftValue={draftValue}
                setDraftValue={setDraftValue}
                setSubmitError={setSubmitError}
                disabled={isSubmitting || hasSubmitted}
                isSubmitting={isSubmitting}
                hasSubmitted={hasSubmitted}
                showCancel={!singleOption}
                onCancel={() => {
                  setSelectedOptionId("");
                  setDraftValue("");
                  setSubmitError(null);
                }}
                onSubmit={() => void handleSubmitSelectedOption()}
                submitLabel={t("tool.submit")}
                submittingLabel={t("tool.submitting")}
                submittedLabel={t("tool.submitted")}
                cancelLabel={t("tool.cancel")}
              />
            )}

            {/* Free-form escape hatch: answer with text the options did not anticipate. Hidden
                when the selected option already opens a text editor. */}
            {!(selectedOption && !customActive && selectedOption.input === "text") &&
              (customActive ? (
                <div className="space-y-2.5 rounded-lg border border-primary/30 bg-background/50 p-3">
                  <Textarea
                    className="min-h-[90px] text-sm focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-input"
                    placeholder={t("tool.customAnswerPlaceholder")}
                    value={customValue}
                    onChange={(e) => {
                      setSubmitError(null);
                      setCustomValue(e.target.value);
                    }}
                    disabled={isSubmitting || hasSubmitted}
                  />
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      size="sm"
                      className="h-8"
                      onClick={() => void handleSubmitCustomAnswer()}
                      disabled={isSubmitting || hasSubmitted}
                    >
                      {isSubmitting ? (
                        <>
                          <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />
                          {t("tool.submitting")}
                        </>
                      ) : (
                        t("tool.submit")
                      )}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="h-8"
                      onClick={() => {
                        setCustomActive(false);
                        setCustomValue("");
                        setSubmitError(null);
                      }}
                      disabled={isSubmitting || hasSubmitted}
                    >
                      {t("tool.cancel")}
                    </Button>
                  </div>
                </div>
              ) : (
                <button
                  type="button"
                  className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-border/80 px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:border-primary/40 hover:text-foreground"
                  onClick={() => {
                    setSelectedOptionId("");
                    setDraftValue("");
                    setSubmitError(null);
                    setCustomActive(true);
                  }}
                  disabled={isSubmitting || hasSubmitted}
                >
                  <PenLine className="h-3.5 w-3.5" />
                  {t("tool.customAnswer")}
                </button>
              ))}
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

function SelectedOptionEditor({
  option,
  draftValue,
  setDraftValue,
  setSubmitError,
  disabled,
  isSubmitting,
  hasSubmitted,
  showCancel,
  onCancel,
  onSubmit,
  submitLabel,
  submittingLabel,
  submittedLabel,
  cancelLabel,
}: {
  option: AskUserQuestionOption;
  draftValue: string;
  setDraftValue: (value: string) => void;
  setSubmitError: (value: string | null) => void;
  disabled: boolean;
  isSubmitting: boolean;
  hasSubmitted: boolean;
  showCancel: boolean;
  onCancel: () => void;
  onSubmit: () => void;
  submitLabel: string;
  submittingLabel: string;
  submittedLabel: string;
  cancelLabel: string;
}) {
  return (
    <div className="space-y-2.5 rounded-lg border border-border/50 bg-background/50 p-3">
      {option.input === "select" ? (
        <div className="space-y-2">
          <div className="text-xs font-medium text-muted-foreground">{option.label}</div>
          <div className="flex flex-wrap gap-1.5">
            {option.choices.map((choice) => {
              const isSelected = draftValue === choice;
              return (
                <Button
                  key={choice}
                  type="button"
                  size="sm"
                  variant={isSelected ? "secondary" : "outline"}
                  className={cn("h-7 rounded-full text-xs", isSelected && "ring-1 ring-ring")}
                  onClick={() => {
                    setSubmitError(null);
                    setDraftValue(choice);
                  }}
                  disabled={disabled}
                >
                  {choice}
                </Button>
              );
            })}
          </div>
        </div>
      ) : option.input === "text" ? (
        <div className="space-y-2">
          <div className="text-xs font-medium text-muted-foreground">{option.label}</div>
          <Textarea
            className="min-h-[120px] font-mono text-xs focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-input"
            placeholder={option.label}
            value={draftValue}
            onChange={(e) => {
              setSubmitError(null);
              setDraftValue(e.target.value);
            }}
            disabled={disabled}
          />
        </div>
      ) : (
        <div className="rounded-md border border-border/50 bg-background/70 px-3 py-2 text-sm">
          {option.label}
        </div>
      )}

      <div className="flex items-center gap-2">
        <Button type="button" size="sm" className="h-8" onClick={onSubmit} disabled={disabled}>
          {isSubmitting || hasSubmitted ? (
            <>
              {isSubmitting && <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" />}
              {isSubmitting ? submittingLabel : submittedLabel}
            </>
          ) : (
            submitLabel
          )}
        </Button>
        {showCancel && (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="h-8"
            onClick={onCancel}
            disabled={disabled}
          >
            {cancelLabel}
          </Button>
        )}
      </div>
    </div>
  );
}
