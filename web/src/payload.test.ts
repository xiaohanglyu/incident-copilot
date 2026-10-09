import { buildRequest, formatOffset } from './payload';

describe('buildRequest', () => {
  const empty = { query: '', since: '', until: '', services: '' };

  it('sends only the query when nothing else is filled in', () => {
    expect(buildRequest({ ...empty, query: '  lag  ' }, 'fallback', 480)).toEqual({ query: 'lag' });
  });

  it('falls back to the placeholder for an empty query', () => {
    expect(buildRequest(empty, 'checkout latency', 480).query).toBe('checkout latency');
  });

  it('attaches the browser offset to local times so the server reads the same instant', () => {
    const request = buildRequest({ ...empty, query: 'q', since: '2026-08-24T14:25', until: '2026-08-24T15:00' }, '', 480);
    expect(request.since).toBe('2026-08-24T14:25:00+08:00');
    expect(request.until).toBe('2026-08-24T15:00:00+08:00');
  });

  it('splits services on commas and drops blanks', () => {
    expect(buildRequest({ ...empty, query: 'q', services: ' checkout-service, ,order-consumer ' }, '', 0).services)
      .toEqual(['checkout-service', 'order-consumer']);
  });
});

describe('formatOffset', () => {
  it.each([
    [480, '+08:00'],
    [0, '+00:00'],
    [-420, '-07:00'],
    [330, '+05:30'],
    [-210, '-03:30'],
  ])('%i minutes is %s', (minutes, expected) => {
    expect(formatOffset(minutes)).toBe(expected);
  });
});
