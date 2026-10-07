export type Lang = 'en' | 'zh';

export interface Sample {
  label: string;
  query: string;
  /** Fixture dates are fixed, so a sample carries its own window. */
  since?: string;
  until?: string;
  services?: string;
}

export interface Messages {
  subtitle: string;
  queryLabel: string;
  placeholder: string;
  submit: string;
  cancel: string;
  since: string;
  until: string;
  servicesLabel: string;
  servicesHint: string;
  optionalHint: string;
  samples: Sample[];
  busy: (seconds: number) => string;
  done: (seconds: number) => string;
  cancelled: string;
  cause: string;
  confidence: (pct: number) => string;
  evidence: string;
  verification: string;
  mitigation: string;
  trail: string;
  trailHint: string;
  waitingForTools: string;
  knowledge: string;
  footer: string;
}

export const MESSAGES: Record<Lang, Messages> = {
  en: {
    subtitle:
      'Describe a production symptom. The agent pulls metrics, logs and recent ' +
      'changes, consults the runbooks, and proposes a cause for you to review.',
    queryLabel: 'Describe the symptom',
    placeholder: 'checkout-service response time suddenly spiked',
    submit: 'Investigate',
    cancel: 'Stop',
    since: 'Symptom noticed from',
    until: 'until',
    servicesLabel: 'Services mentioned',
    servicesHint: 'comma separated',
    optionalHint: 'All three are optional. Left empty, the agent works it out itself.',
    samples: [
      { label: 'checkout latency', query: 'checkout-service response time suddenly spiked', since: '2026-08-24T14:25' },
      { label: 'consumer lag (crosses midnight)', query: 'Why is the order consumer lagging?', since: '2026-08-24T00:15' },
      { label: 'no service named', query: 'kafka message backlog, what is going on?' },
    ],
    busy: (s) => `Investigating… ${s}s`,
    done: (s) => `Completed in ${s}s`,
    cancelled: 'Stopped.',
    cause: 'Most likely cause',
    confidence: (pct) => `confidence ${pct}%`,
    evidence: 'Evidence',
    verification: 'Suggested verification',
    mitigation: 'Suggested mitigation',
    trail: 'Investigation trail',
    trailHint: 'Recorded server-side as the tools ran — not the model’s account of itself.',
    waitingForTools: 'Waiting for the first tool call…',
    knowledge: 'Retrieved knowledge',
    footer: 'Read-only tools. Every action is a proposal — nothing is executed.',
  },
  zh: {
    subtitle: '描述一个线上现象。Agent 会自行拉取指标、日志和最近变更，检索 runbook，给出一份待你审阅的判断。',
    queryLabel: '描述现象',
    placeholder: 'checkout-service 响应时间突然变高',
    submit: '开始调查',
    cancel: '停止',
    since: '症状被注意到的时间',
    until: '至',
    servicesLabel: '涉及服务',
    servicesHint: '逗号分隔',
    optionalHint: '三项均可留空。留空时由 Agent 自己判断。',
    samples: [
      { label: 'checkout 变慢', query: 'checkout-service 响应时间突然变高', since: '2026-08-24T14:25' },
      { label: '消费积压（跨午夜）', query: 'order-consumer 消息堆积严重，怎么回事', since: '2026-08-24T00:15' },
      { label: '不指名服务', query: 'kafka 消息堆积怎么回事' },
    ],
    busy: (s) => `调查中… ${s} 秒`,
    done: (s) => `耗时 ${s} 秒`,
    cancelled: '已停止。',
    cause: '最可能的原因',
    confidence: (pct) => `置信度 ${pct}%`,
    evidence: '证据',
    verification: '验证建议',
    mitigation: '缓解建议',
    trail: '调查过程',
    trailHint: '由服务端在工具执行时记录，不是模型对自己行为的复述。',
    waitingForTools: '等待第一次工具调用…',
    knowledge: '检索到的知识',
    footer: '所有工具只读。任何动作都只是建议，不会被执行。',
  },
};

export function initialLang(stored: string | null, browser: string): Lang {
  if (stored === 'en' || stored === 'zh') return stored;
  return browser.toLowerCase().startsWith('zh') ? 'zh' : 'en';
}
