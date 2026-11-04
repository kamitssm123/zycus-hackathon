import { env } from '../lib/env';
import type { ApiErrorBody } from './types';

/** Thrown for every non-2xx response. Carries the backend's code + requestId for debugging. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.message || `Request failed with ${status}`);
    this.name = 'ApiError';
  }

  /** Flattened field errors, convenient for form rendering. */
  get fieldErrors(): Record<string, string> {
    return Object.fromEntries((this.body.details ?? []).map((d) => [d.field, d.message]));
  }
}

type Options = Omit<RequestInit, 'body'> & { body?: unknown };

/**
 * The single place that talks HTTP. Components and hooks never call fetch directly — that keeps
 * error shape, base URL and headers consistent across the whole app.
 */
export async function api<T>(path: string, { body, ...init }: Options = {}): Promise<T> {
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload ?? { code: 'NETWORK_ERROR', message: 'Unexpected error' },
    );
  }

  return payload as T;
}
