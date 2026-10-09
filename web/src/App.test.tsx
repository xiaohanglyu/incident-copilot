import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';

/** A fetch Response whose body is released one SSE event at a time, on demand. */
function controllableStream() {
  const encoder = new TextEncoder();
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: (c) => { controller = c; } });
  return {
    response: new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }),
    emit: (event: string, data: unknown) =>
      controller.enqueue(encoder.encode(`event:${event}\ndata:${JSON.stringify(data)}\n\n`)),
    close: () => controller.close(),
  };
}

const toolCall = (tool: string) => ({ tool, arguments: 'service=checkout-service', result: `${tool} output`, millis: 2 });

beforeEach(() => {
  localStorage.setItem('lang', 'en');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('shows each tool call while the agent works, then the report', async () => {
    const stream = controllableStream();
    const fetchMock = vi.fn().mockResolvedValue(stream.response);
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await user.type(screen.getByRole('textbox', { name: 'Describe the symptom' }), 'checkout is slow');
    await user.click(screen.getByRole('button', { name: 'Investigate' }));

    expect(fetchMock).toHaveBeenCalledWith('/api/investigate/stream', expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(fetchMock.mock.calls[0]![1].body)).toEqual({ query: 'checkout is slow' });
    expect(await screen.findByText('Waiting for the first tool call…')).toBeInTheDocument();

    stream.emit('tool-call', toolCall('getMetrics'));
    const trail = await screen.findByRole('list', { name: 'Investigation trail' });
    expect(within(trail).getByText('getMetrics')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeInTheDocument();

    stream.emit('tool-call', toolCall('searchLogs'));
    expect(await within(trail).findByText('searchLogs')).toBeInTheDocument();

    stream.emit('result', {
      report: {
        mostLikelyCause: 'Connection pool exhaustion after the 14:20 deploy',
        confidence: 0.42,
        evidence: [{ source: 'getMetrics', detail: 'pool at 50/50' }],
        suggestedVerification: [],
        suggestedMitigation: ['Roll back checkout-service'],
      },
      toolCalls: [toolCall('getMetrics'), toolCall('searchLogs')],
      retrievedKnowledge: [],
    });
    stream.close();

    expect(await screen.findByText('Connection pool exhaustion after the 14:20 deploy')).toBeInTheDocument();
    expect(screen.getByText('confidence 42%')).toHaveClass('low');
    expect(screen.getByText('Roll back checkout-service')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/Completed in \d+s/);
    expect(screen.getByRole('button', { name: 'Investigate' })).toBeEnabled();
  });

  it('shows a server-side failure as an alert', async () => {
    const stream = controllableStream();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(stream.response));
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'checkout latency' }));
    stream.emit('error', { message: 'model unavailable' });
    stream.close();

    expect(await screen.findByRole('alert')).toHaveTextContent('model unavailable');
  });

  it('stops a running investigation and aborts the request', async () => {
    const stream = controllableStream();
    const fetchMock = vi.fn().mockResolvedValue(stream.response);
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'checkout latency' }));
    await user.click(await screen.findByRole('button', { name: 'Stop' }));

    const signal: AbortSignal = fetchMock.mock.calls[0]![1].signal;
    expect(signal.aborted).toBe(true);
    expect(screen.getByRole('status')).toHaveTextContent('Stopped.');
    expect(screen.getByRole('button', { name: 'Investigate' })).toBeEnabled();
  });

  it('switches language and remembers the choice', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: '中文' }));

    expect(screen.getByRole('button', { name: '开始调查' })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('zh-CN');
    expect(localStorage.getItem('lang')).toBe('zh');
  });
});
