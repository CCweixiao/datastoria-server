/** Display-only response contract for the Java AgentScope skill review endpoint. */
export type SkillReviewResponse = {
  findings: string;
  proposals: Array<{
    path: string;
    reason: string;
    updatedContent: string;
  }>;
};
