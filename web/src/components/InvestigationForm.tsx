import type { FormEvent } from 'react';
import type { Messages, Sample } from '../i18n';
import type { FormValues } from '../payload';

interface Props {
  t: Messages;
  values: FormValues;
  running: boolean;
  onChange: (values: FormValues) => void;
  onSubmit: (values: FormValues) => void;
  onCancel: () => void;
}

export function InvestigationForm({ t, values, running, onChange, onSubmit, onCancel }: Props) {
  const set = (field: keyof FormValues) => (value: string) => onChange({ ...values, [field]: value });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit(values);
  };

  const runSample = (sample: Sample) => {
    const next = {
      query: sample.query,
      since: sample.since ?? '',
      until: sample.until ?? '',
      services: sample.services ?? '',
    };
    onChange(next);
    onSubmit(next);
  };

  return (
    <>
      <form onSubmit={submit} aria-busy={running}>
        <label className="visually-hidden" htmlFor="query">{t.queryLabel}</label>
        <textarea
          id="query"
          rows={3}
          spellCheck={false}
          placeholder={t.placeholder}
          value={values.query}
          disabled={running}
          onChange={(e) => set('query')(e.target.value)}
        />
        <div className="optional">
          <label>
            <span>{t.since}</span>
            <input type="datetime-local" value={values.since} disabled={running}
              onChange={(e) => set('since')(e.target.value)} />
          </label>
          <label>
            <span>{t.until}</span>
            <input type="datetime-local" value={values.until} disabled={running}
              onChange={(e) => set('until')(e.target.value)} />
          </label>
          <label className="grow">
            <span>{t.servicesLabel}</span>
            <input type="text" autoComplete="off" spellCheck={false} placeholder={t.servicesHint}
              value={values.services} disabled={running}
              onChange={(e) => set('services')(e.target.value)} />
          </label>
        </div>
        <div className="actions">
          <span className="hint-inline">{t.optionalHint}</span>
          {running ? (
            <button type="button" className="primary secondary" onClick={onCancel}>{t.cancel}</button>
          ) : (
            <button type="submit" className="primary">{t.submit}</button>
          )}
        </div>
      </form>

      <div className="samples">
        {t.samples.map((sample) => (
          <button key={sample.label} type="button" disabled={running} onClick={() => runSample(sample)}>
            {sample.label}
          </button>
        ))}
      </div>
    </>
  );
}
