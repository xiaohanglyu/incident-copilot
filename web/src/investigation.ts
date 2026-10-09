import type { InvestigationEvent, InvestigationResult, KnowledgeSnippet, ToolCall } from './api/types';

export interface InvestigationState {
  status: 'idle' | 'running' | 'done' | 'failed' | 'cancelled';
  knowledge: KnowledgeSnippet[];
  /** Grows while the agent works; replaced by the server's list once the result lands. */
  toolCalls: ToolCall[];
  result: InvestigationResult | null;
  error: string | null;
  /** Epoch millis; set on start, and on whatever ends the run. */
  startedAt: number | null;
  endedAt: number | null;
}

export type Action =
  | { type: 'start'; at: number }
  | { type: 'event'; event: InvestigationEvent; at: number }
  | { type: 'failed'; message: string; at: number }
  | { type: 'cancelled'; at: number };

export const initialState: InvestigationState = {
  status: 'idle',
  knowledge: [],
  toolCalls: [],
  result: null,
  error: null,
  startedAt: null,
  endedAt: null,
};

export function reducer(state: InvestigationState, action: Action): InvestigationState {
  switch (action.type) {
    case 'start':
      return { ...initialState, status: 'running', startedAt: action.at };
    case 'failed':
      return { ...state, status: 'failed', error: action.message, endedAt: action.at };
    case 'cancelled':
      return state.status === 'running' ? { ...state, status: 'cancelled', endedAt: action.at } : state;
    case 'event': {
      // A late event after a cancel or failure must not resurrect the run.
      if (state.status !== 'running') return state;
      const next = applyEvent(state, action.event);
      return next.status === 'running' ? next : { ...next, endedAt: action.at };
    }
  }
}

function applyEvent(state: InvestigationState, event: InvestigationEvent): InvestigationState {
  switch (event.type) {
    case 'knowledge':
      return { ...state, knowledge: event.snippets };
    case 'tool-call':
      return { ...state, toolCalls: [...state.toolCalls, event.call] };
    case 'result':
      // The result's trail is authoritative: it is what the server recorded, in order.
      return {
        ...state,
        status: 'done',
        result: event.result,
        toolCalls: event.result.toolCalls,
        knowledge: event.result.retrievedKnowledge,
      };
    case 'error':
      return { ...state, status: 'failed', error: event.message };
  }
}
