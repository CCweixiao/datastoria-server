export type AskUserQuestionOption =
  | { id: string; label: string; input: "none" }
  | { id: string; label: string; input: "text" }
  | { id: string; label: string; input: "select"; choices: string[] };

export type AskUserQuestionInput = {
  questions: {
    header: string;
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
