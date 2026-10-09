import type { KnowledgeSnippet, Report } from '../api/types';
import type { Messages } from '../i18n';
import { Card } from './Card';

/** Below this the confidence badge turns amber: worth a second look before acting. */
const LOW_CONFIDENCE = 60;

export function ReportView({ t, report }: { t: Messages; report: Report }) {
  const pct = Math.round((report.confidence ?? 0) * 100);
  return (
    <>
      <Card title={t.cause}>
        <p className="cause">{report.mostLikelyCause}</p>
        <span className={`confidence${pct < LOW_CONFIDENCE ? ' low' : ''}`}>{t.confidence(pct)}</span>
      </Card>

      {report.evidence?.length > 0 && (
        <Card title={t.evidence}>
          <ul className="evidence">
            {report.evidence.map((item, i) => (
              <li key={i}>
                <span className={`src${item.source === 'knowledge' ? ' knowledge' : ''}`}>{item.source}</span>
                <span>{item.detail}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {report.suggestedVerification?.length > 0 && (
        <Card title={t.verification}><PlainList items={report.suggestedVerification} /></Card>
      )}
      {report.suggestedMitigation?.length > 0 && (
        <Card title={t.mitigation}><PlainList items={report.suggestedMitigation} /></Card>
      )}
    </>
  );
}

export function KnowledgeView({ t, snippets }: { t: Messages; snippets: KnowledgeSnippet[] }) {
  if (snippets.length === 0) return null;
  return (
    <Card title={t.knowledge}>
      {snippets.map((snippet) => (
        <details key={snippet.source}>
          <summary>{snippet.source}</summary>
          <pre>{snippet.content}</pre>
        </details>
      ))}
    </Card>
  );
}

function PlainList({ items }: { items: string[] }) {
  return (
    <ul className="plain">
      {items.map((item, i) => <li key={i}>{item}</li>)}
    </ul>
  );
}
