import { parseSse, type SseMessage } from './sse';

function streamOf(...chunks: (string | Uint8Array)[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk);
      controller.close();
    },
  });
}

async function collect(stream: ReadableStream<Uint8Array>): Promise<SseMessage[]> {
  const messages: SseMessage[] = [];
  for await (const message of parseSse(stream)) messages.push(message);
  return messages;
}

describe('parseSse', () => {
  it('parses named events in order, the way Spring writes them', async () => {
    const messages = await collect(
      streamOf('event:knowledge\ndata:[]\n\nevent:tool-call\ndata:{"tool":"getMetrics"}\n\n'),
    );
    expect(messages).toEqual([
      { event: 'knowledge', data: '[]' },
      { event: 'tool-call', data: '{"tool":"getMetrics"}' },
    ]);
  });

  it('reassembles an event split across chunks', async () => {
    const messages = await collect(streamOf('event:res', 'ult\ndata:{"a"', ':1}\n', '\n'));
    expect(messages).toEqual([{ event: 'result', data: '{"a":1}' }]);
  });

  it('keeps a multi-byte character split across chunks intact', async () => {
    const bytes = new TextEncoder().encode('data:调查\n\n');
    const messages = await collect(streamOf(bytes.slice(0, 7), bytes.slice(7)));
    expect(messages).toEqual([{ event: 'message', data: '调查' }]);
  });

  it('joins multi-line data, ignores comments and accepts CRLF', async () => {
    const messages = await collect(streamOf(': keep-alive\r\ndata: first\r\ndata: second\r\n\r\n'));
    expect(messages).toEqual([{ event: 'message', data: 'first\nsecond' }]);
  });

  it('delivers a final event that lacks the trailing blank line', async () => {
    const messages = await collect(streamOf('event:error\ndata:{"message":"boom"}'));
    expect(messages).toEqual([{ event: 'error', data: '{"message":"boom"}' }]);
  });
});
