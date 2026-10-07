import { expect, test, type Page } from '@playwright/test';

/**
 * Drives the built page in a real browser. The model is the one thing stubbed: the
 * streaming endpoint is answered with a recorded event sequence, so the run is
 * deterministic and needs no API key.
 */

const toolCall = (tool: string, result: string) => ({
  tool,
  arguments: 'service=checkout-service',
  result,
  millis: 3,
});

const RECORDED = [
  ['knowledge', [{ source: 'connection-pool-exhaustion.md', content: 'Rising latency with flat CPU…' }]],
  ['tool-call', toolCall('getMetrics', 'p99 118ms → 3.4s, pool 50/50')],
  ['tool-call', toolCall('searchLogs', 'HikariPool-1 - Connection is not available')],
  ['tool-call', toolCall('getRecentChanges', 'v2025.8.19 modified OrderRepository')],
  ['result', {
    report: {
      mostLikelyCause: 'Connection pool exhaustion caused by v2025.8.19',
      confidence: 0.95,
      evidence: [
        { source: 'getMetrics', detail: 'pool hit 50/50 from 14:25' },
        { source: 'knowledge', detail: 'runbook: flat CPU and a saturated pool is pool exhaustion' },
      ],
      suggestedVerification: ['Check query latency for the modified SQL'],
      suggestedMitigation: ['Roll back checkout-service to v2025.8.18'],
    },
    toolCalls: [
      toolCall('getMetrics', 'p99 118ms → 3.4s, pool 50/50'),
      toolCall('searchLogs', 'HikariPool-1 - Connection is not available'),
      toolCall('getRecentChanges', 'v2025.8.19 modified OrderRepository'),
    ],
    retrievedKnowledge: [{ source: 'connection-pool-exhaustion.md', content: 'Rising latency with flat CPU…' }],
  }],
] as const;

const sse = (events: readonly (readonly [string, unknown])[]) =>
  events.map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`).join('');

async function stubStream(page: Page, body: string) {
  const requests: unknown[] = [];
  await page.route('**/api/investigate/stream', async (route) => {
    requests.push(route.request().postDataJSON());
    await route.fulfill({ status: 200, contentType: 'text/event-stream', body });
  });
  return requests;
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('lang', 'en'));
});

test('a sample investigation renders the report, trail and knowledge', async ({ page }) => {
  const requests = await stubStream(page, sse(RECORDED));
  await page.goto('/');

  await page.getByRole('button', { name: 'checkout latency' }).click();

  await expect(page.getByText('Connection pool exhaustion caused by v2025.8.19')).toBeVisible();
  await expect(page.getByText('confidence 95%')).toBeVisible();
  await expect(page.getByRole('list', { name: 'Investigation trail' }).getByRole('listitem'))
    .toHaveText(['getMetrics', 'searchLogs', 'getRecentChanges']);
  await expect(page.getByRole('status')).toHaveText(/Completed in \d+s/);

  // The sample carries its own window, sent with the browser's offset attached.
  expect(requests).toHaveLength(1);
  expect(requests[0]).toMatchObject({
    query: 'checkout-service response time suddenly spiked',
    since: expect.stringMatching(/^2026-08-24T14:25:00[+-]\d{2}:\d{2}$/),
  });

  // A tool call expands to the raw output the agent saw.
  await page.getByText('searchLogs(service=checkout-service)').click();
  await expect(page.getByText('HikariPool-1 - Connection is not available')).toBeVisible();
});

test('the form sends what was typed, including services', async ({ page }) => {
  const requests = await stubStream(page, sse(RECORDED));
  await page.goto('/');

  await page.getByRole('textbox', { name: 'Describe the symptom' }).fill('orders are stuck');
  await page.getByRole('textbox', { name: 'Services mentioned' }).fill('order-consumer, checkout-service');
  await page.getByRole('button', { name: 'Investigate' }).click();

  await expect(page.getByText('Connection pool exhaustion caused by v2025.8.19')).toBeVisible();
  expect(requests[0]).toEqual({ query: 'orders are stuck', services: ['order-consumer', 'checkout-service'] });
});

test('a failure from the server is shown as an alert', async ({ page }) => {
  await stubStream(page, sse([['error', { message: 'model unavailable' }]]));
  await page.goto('/');

  await page.getByRole('button', { name: 'no service named' }).click();

  await expect(page.getByRole('alert')).toHaveText('model unavailable');
  await expect(page.getByRole('button', { name: 'Investigate' })).toBeEnabled();
});

test('a rejected request surfaces the HTTP status', async ({ page }) => {
  await page.route('**/api/investigate/stream', (route) =>
    route.fulfill({ status: 400, contentType: 'application/problem+json', body: '{"title":"Bad Request"}' }));
  await page.goto('/');

  await page.getByRole('button', { name: 'Investigate' }).click();

  await expect(page.getByRole('alert')).toContainText('HTTP 400');
});

test('the page works in Chinese and is keyboard operable', async ({ page }) => {
  await stubStream(page, sse(RECORDED));
  await page.goto('/');

  await page.getByRole('button', { name: '中文' }).click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');

  await page.getByRole('textbox', { name: '描述现象' }).focus();
  await page.keyboard.type('checkout 很慢');
  await page.getByRole('button', { name: '开始调查' }).focus();
  await page.keyboard.press('Enter');

  await expect(page.getByText('Connection pool exhaustion caused by v2025.8.19')).toBeVisible();
  await expect(page.getByRole('status')).toHaveText(/耗时 \d+ 秒/);
});
