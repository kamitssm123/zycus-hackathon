import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { providersApi, suggestionsApi } from './scheduling';

function mockFetch(status: number, body: unknown) {
  const spy = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(body === null ? '' : JSON.stringify(body)),
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

describe('scheduling api', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('accepts a suggestion with the right method and body', async () => {
    const spy = mockFetch(200, { suggestionId: 'SGT-001', suggestionStatus: 'ACCEPTED' });

    await suggestionsApi.decide('SGT-001', 'ACCEPTED');

    const [url, init] = spy.mock.calls[0]!;
    expect(url).toBe('/api/suggestions/SGT-001');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ status: 'ACCEPTED' });
  });

  it('filters the suggestion list by status', async () => {
    const spy = mockFetch(200, []);
    await suggestionsApi.list('PENDING');
    expect(spy.mock.calls[0]![0]).toBe('/api/suggestions?status=PENDING');
  });

  it('surfaces a lost race as an ApiError carrying the backend code and requestId', async () => {
    mockFetch(409, {
      code: 'CONFLICT',
      message: 'Slot SLT-005 is no longer available (BOOKED)',
      requestId: 'a1b2c3d4',
    });

    const error: unknown = await suggestionsApi
      .decide('SGT-002', 'ACCEPTED')
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.body.code).toBe('CONFLICT');
    // The requestId is what ties this toast to a server log line (D-04).
    expect(apiError.body.requestId).toBe('a1b2c3d4');
  });

  it('patches provider status, which is what fires the agentic loop', async () => {
    const spy = mockFetch(200, { id: 'PRV-001', status: 'OFFLINE' });

    await providersApi.setStatus('PRV-001', 'OFFLINE');

    const [url, init] = spy.mock.calls[0]!;
    expect(url).toBe('/api/providers/PRV-001/status');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ status: 'OFFLINE' });
  });
});
