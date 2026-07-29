"use client";

import { createContext, useContext } from "react";

export type UserActionInput = {
  text: string;
  autoRun?: boolean;
};

interface ChatActionContextType {
  onAction: (input: UserActionInput) => void;
  onToolOutput: (input: {
    runId: string;
    actionId: string;
    toolCallId: string;
    output: unknown;
  }) => Promise<void>;
  onApproval: (input: { runId: string; actionId: string; approved: boolean }) => Promise<void>;
  chatId?: string;
}

const ChatActionContext = createContext<ChatActionContextType | undefined>(undefined);

export function useChatAction() {
  const context = useContext(ChatActionContext);
  if (!context) {
    throw new Error("useChatAction must be used within a ChatActionProvider");
  }
  return context;
}

export function ChatActionProvider({
  children,
  onAction,
  onToolOutput,
  onApproval,
  chatId,
}: {
  children: React.ReactNode;
  onAction: (input: UserActionInput) => void;
  onToolOutput: ChatActionContextType["onToolOutput"];
  onApproval: ChatActionContextType["onApproval"];
  chatId?: string;
}) {
  return (
    <ChatActionContext.Provider value={{ onAction, onToolOutput, onApproval, chatId }}>
      {children}
    </ChatActionContext.Provider>
  );
}
