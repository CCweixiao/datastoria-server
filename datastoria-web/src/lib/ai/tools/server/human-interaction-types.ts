type AskUserQuestionOptionBase = {
  id: string;
  label: string;
  /** Optional secondary line explaining what this choice does. */
  description?: string;
};

export type AskUserQuestionOption =
  | (AskUserQuestionOptionBase & { input: "none" })
  | (AskUserQuestionOptionBase & { input: "text" })
  | (AskUserQuestionOptionBase & { input: "select"; choices: string[] });

export type AskUserQuestionInput = {
  questions: {
    header: string;
    /** Optional context line shown under the header. */
    description?: string;
    options: AskUserQuestionOption[];
  }[];
};

export type AskUserQuestionOutput = {
  optionId: string;
  label: string;
  input: "none" | "text" | "select";
  value: string;
};

export const HUMAN_INTERACTION_TOOL_NAMES = {
  ASK_USER_QUESTION: "ask_user_question",
} as const;
