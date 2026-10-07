import { useEffect, useState } from 'react';
import { InvestigationForm } from './components/InvestigationForm';
import { KnowledgeView, ReportView } from './components/ReportView';
import { Trail } from './components/Trail';
import { initialLang, MESSAGES, type Lang } from './i18n';
import { buildRequest, type FormValues } from './payload';
import { useElapsedSeconds, useInvestigation } from './useInvestigation';

const EMPTY: FormValues = { query: '', since: '', until: '', services: '' };

export function App() {
  const [lang, setLang] = useState<Lang>(() => initialLang(readStoredLang(), navigator.language ?? ''));
  const [values, setValues] = useState<FormValues>(EMPTY);
  const { state, start, cancel } = useInvestigation();
  const seconds = useElapsedSeconds(state.startedAt, state.endedAt);
  const t = MESSAGES[lang];
  const running = state.status === 'running';

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
  }, [lang]);

  const chooseLang = (next: Lang) => {
    setLang(next);
    try {
      localStorage.setItem('lang', next);
    } catch {
      // Private mode or blocked storage: the choice just won't survive a reload.
    }
  };

  return (
    <div className="wrap">
      <header>
        <div>
          <h1>incident-copilot</h1>
          <p className="subtitle">{t.subtitle}</p>
        </div>
        <div className="lang" role="group" aria-label="Language">
          {(['en', 'zh'] as const).map((code) => (
            <button key={code} type="button" aria-pressed={lang === code} onClick={() => chooseLang(code)}>
              {code === 'en' ? 'EN' : '中文'}
            </button>
          ))}
        </div>
      </header>

      <main>
        <InvestigationForm
          t={t}
          values={values}
          running={running}
          onChange={setValues}
          onSubmit={(v) => void start(buildRequest(v, t.placeholder))}
          onCancel={cancel}
        />

        <div className="status" role="status" aria-live="polite">
          {running && <><span className="dot" aria-hidden="true" />{t.busy(seconds)}</>}
          {state.status === 'done' && t.done(seconds)}
          {state.status === 'cancelled' && t.cancelled}
        </div>

        {state.status === 'failed' && (
          <div className="card error" role="alert">{state.error}</div>
        )}
        {state.result && <ReportView t={t} report={state.result.report} />}
        {state.status !== 'idle' && (running || state.toolCalls.length > 0) && (
          <Trail t={t} calls={state.toolCalls} running={running} />
        )}
        <KnowledgeView t={t} snippets={state.knowledge} />
      </main>

      <footer>{t.footer}</footer>
    </div>
  );
}

function readStoredLang(): string | null {
  try {
    return localStorage.getItem('lang');
  } catch {
    return null;
  }
}
