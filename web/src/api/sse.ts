export interface SseMessage {
  event: string;
  data: string;
}

/**
 * Parses a text/event-stream body. EventSource would do this for us, but it only issues
 * GET requests, and an investigation is a POST with a JSON body.
 *
 * Handles what the spec allows and servers actually send: LF or CRLF line endings,
 * multi-line `data:`, comments, and events or characters split across network chunks.
 */
export async function* parseSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseMessage> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let event = 'message';
  let data: string[] = [];

  // Returns the finished event when `line` is the blank line that ends one.
  const accept = (line: string): SseMessage | null => {
    if (line === '') {
      // An event with no data is a no-op per the spec.
      const message = data.length ? { event, data: data.join('\n') } : null;
      event = 'message';
      data = [];
      return message;
    }
    if (line.startsWith(':')) return null;

    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);

    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
    return null;
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      // `stream: true` keeps a multi-byte character split across chunks (中文) intact.
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });

      let newline: number;
      while ((newline = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, newline).replace(/\r$/, '');
        buffer = buffer.slice(newline + 1);
        const message = accept(line);
        if (message) yield message;
      }

      if (done) {
        // A stream that ends without the trailing blank line still delivered its last event.
        if (buffer) accept(buffer.replace(/\r$/, ''));
        const message = accept('');
        if (message) yield message;
        return;
      }
    }
  } finally {
    reader.releaseLock();
  }
}
