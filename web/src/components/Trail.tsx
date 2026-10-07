import type { ToolCall } from '../api/types';
import type { Messages } from '../i18n';
import { Card } from './Card';

/**
 * The tool calls as the server recorded them. Rendered while the agent is still working,
 * so a reader watches the investigation happen instead of staring at a spinner.
 */
export function Trail({ t, calls, running }: { t: Messages; calls: ToolCall[]; running: boolean }) {
  return (
    <Card title={t.trail}>
      {calls.length > 0 && (
        <ol className="trace" aria-label={t.trail}>
          {calls.map((call, i) => (
            <li key={i}>{call.tool}</li>
          ))}
          {running && <li className="pending" aria-hidden="true">…</li>}
        </ol>
      )}
      <p className="hint">{calls.length === 0 && running ? t.waitingForTools : t.trailHint}</p>
      {calls.map((call, i) => (
        <details key={i} data-testid="tool-call">
          <summary>
            {call.tool}({call.arguments}) <em>{call.millis}ms</em>
          </summary>
          <pre>{call.result}</pre>
        </details>
      ))}
    </Card>
  );
}
