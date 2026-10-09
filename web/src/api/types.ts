// Mirrors the Java records in dev.xiaohanglyu.incidentcopilot. Keep the two in step.

export interface InvestigationRequest {
  query: string;
  /** ISO 8601 with offset. When the symptom was noticed, not when the cause happened. */
  since?: string;
  until?: string;
  services?: string[];
}

export interface Evidence {
  /** A tool name, or `knowledge` for a runbook. */
  source: string;
  detail: string;
}

export interface Report {
  mostLikelyCause: string;
  confidence: number;
  evidence: Evidence[];
  suggestedVerification: string[];
  suggestedMitigation: string[];
}

export interface ToolCall {
  tool: string;
  arguments: string;
  result: string;
  millis: number;
}

export interface KnowledgeSnippet {
  source: string;
  content: string;
}

export interface InvestigationResult {
  report: Report;
  toolCalls: ToolCall[];
  retrievedKnowledge: KnowledgeSnippet[];
}

/** One Server-Sent Event from POST /api/investigate/stream, already parsed. */
export type InvestigationEvent =
  | { type: 'knowledge'; snippets: KnowledgeSnippet[] }
  | { type: 'tool-call'; call: ToolCall }
  | { type: 'result'; result: InvestigationResult }
  | { type: 'error'; message: string };
