import { parseSse } from './sse';
import type {
  InvestigationEvent,
  InvestigationRequest,
  InvestigationResult,
  KnowledgeSnippet,
  ToolCall,
} from './types';

/**
 * Starts an investigation and yields its events as they arrive. Ends after `result` or
 * `error`; aborting `signal` cancels the request, which also stops the server-side run.
 */
export async function* investigate(
  request: InvestigationRequest,
  signal?: AbortSignal,
): AsyncGenerator<InvestigationEvent> {
  const response = await fetch('/api/investigate/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(request),
    signal,
  });
  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status}\n\n${await response.text()}`);
  }

  for await (const message of parseSse(response.body)) {
    const event = toEvent(message.event, JSON.parse(message.data));
    if (event) yield event;
  }
}

function toEvent(name: string, data: unknown): InvestigationEvent | null {
  switch (name) {
    case 'knowledge':
      return { type: 'knowledge', snippets: data as KnowledgeSnippet[] };
    case 'tool-call':
      return { type: 'tool-call', call: data as ToolCall };
    case 'result':
      return { type: 'result', result: data as InvestigationResult };
    case 'error':
      return { type: 'error', message: (data as { message?: string }).message ?? 'Unknown error' };
    default:
      // Unknown event names are skipped so the server can add events without breaking us.
      return null;
  }
}
