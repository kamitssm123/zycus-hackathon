import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

const mockFetch = (status: number, body: unknown) =>
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      text: () => Promise.resolve(body === null ? '' : JSON.stringify(body)),
    }),
  );

describe('api client', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('returns parsed json on success', async () => {
    mockFetch(200, { id: '1' });
    await expect(api<{ id: string }>('/api/items')).resolves.toEqual({ id: '1' });
  });

  it('throws ApiError carrying code and field errors', async () => {
    mockFetch(400, {
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      details: [{ field: 'name', message: 'must not be blank' }],
    });

    const error: unknown = await api('/api/items').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.body.code).toBe('VALIDATION_FAILED');
    expect(apiError.fieldErrors).toEqual({ name: 'must not be blank' });
  });

  it('handles 204 with no body', async () => {
    mockFetch(204, null);
    await expect(api('/api/items/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
