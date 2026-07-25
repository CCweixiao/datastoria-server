export interface CommandCatalogItem {
  /** Slash command name. */
  name: string;
  /** One-line description shown in the slash command catalog. */
  description: string;
  /** Stable skill folder id this command belongs to. */
  skillId: string;
  /** Whether this command should be shown as a SQL editor quick action. */
  showInSqlEditorQuickAction?: boolean;
}

export interface CommandDetail extends CommandCatalogItem {
  /** Prompt template. $ARGUMENTS is replaced with user input at submit time. */
  template: string;
}
