import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { investigate } from './api/investigate';
import type { InvestigationRequest } from './api/types';
import { initialState, reducer } from './investigation';

/** Runs one investigation at a time; starting a new one or unmounting cancels the last. */
export function useInvestigation() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const controller = useRef<AbortController | null>(null);

  const start = useCallback(async (request: InvestigationRequest) => {
    controller.current?.abort();
    const current = new AbortController();
    controller.current = current;
    dispatch({ type: 'start', at: Date.now() });

    try {
      for await (const event of investigate(request, current.signal)) {
        dispatch({ type: 'event', event, at: Date.now() });
      }
    } catch (error) {
      if (current.signal.aborted) return;
      const message = error instanceof Error ? error.message : String(error);
      dispatch({ type: 'failed', message, at: Date.now() });
    }
  }, []);

  const cancel = useCallback(() => {
    controller.current?.abort();
    dispatch({ type: 'cancelled', at: Date.now() });
  }, []);

  useEffect(() => () => controller.current?.abort(), []);

  return { state, start, cancel };
}

/** Whole seconds between `from` and `to`, ticking against the clock while `to` is unset. */
export function useElapsedSeconds(from: number | null, to: number | null): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (from === null || to !== null) return;
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, [from, to]);

  if (from === null) return 0;
  return Math.max(0, Math.round(((to ?? now) - from) / 1000));
}
