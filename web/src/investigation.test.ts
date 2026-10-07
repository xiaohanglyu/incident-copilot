import type { InvestigationResult, ToolCall } from './api/types';
import { initialState, reducer, type InvestigationState } from './investigation';

const call = (tool: string): ToolCall => ({ tool, arguments: '', result: '', millis: 1 });

const result: InvestigationResult = {
  report: {
    mostLikelyCause: 'Pool exhaustion',
    confidence: 0.9,
    evidence: [],
    suggestedVerification: [],
    suggestedMitigation: [],
  },
  toolCalls: [call('getMetrics'), call('searchLogs')],
  retrievedKnowledge: [{ source: 'connection-pool-exhaustion.md', content: '…' }],
};

const running = (): InvestigationState => reducer(initialState, { type: 'start', at: 1_000 });

describe('investigation reducer', () => {
  it('appends tool calls as they stream in', () => {
    let state = running();
    state = reducer(state, { type: 'event', event: { type: 'tool-call', call: call('getMetrics') }, at: 2_000 });
    state = reducer(state, { type: 'event', event: { type: 'tool-call', call: call('searchLogs') }, at: 3_000 });

    expect(state.status).toBe('running');
    expect(state.toolCalls.map((c) => c.tool)).toEqual(['getMetrics', 'searchLogs']);
    expect(state.endedAt).toBeNull();
  });

  it('takes the trail from the result, since that is what the server recorded', () => {
    let state = reducer(running(), { type: 'event', event: { type: 'tool-call', call: call('getMetrics') }, at: 2_000 });
    state = reducer(state, { type: 'event', event: { type: 'result', result }, at: 9_000 });

    expect(state.status).toBe('done');
    expect(state.toolCalls).toEqual(result.toolCalls);
    expect(state.knowledge).toEqual(result.retrievedKnowledge);
    expect(state.endedAt).toBe(9_000);
  });

  it('ends the run on an error event', () => {
    const state = reducer(running(), { type: 'event', event: { type: 'error', message: 'model unavailable' }, at: 5_000 });
    expect(state).toMatchObject({ status: 'failed', error: 'model unavailable', endedAt: 5_000 });
  });

  it('ignores events that arrive after the run was stopped', () => {
    const cancelled = reducer(running(), { type: 'cancelled', at: 2_000 });
    const after = reducer(cancelled, { type: 'event', event: { type: 'result', result }, at: 3_000 });

    expect(after).toBe(cancelled);
    expect(after.status).toBe('cancelled');
  });

  it('starts each run from a clean slate', () => {
    const done = reducer(running(), { type: 'event', event: { type: 'result', result }, at: 9_000 });
    const again = reducer(done, { type: 'start', at: 10_000 });

    expect(again).toEqual({ ...initialState, status: 'running', startedAt: 10_000 });
  });
});
