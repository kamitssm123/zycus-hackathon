import { api } from './client';
import type { Item, ItemPayload, PageResponse } from './types';

/** One module per resource; thin wrappers so hooks stay free of URL strings. */
export const itemsApi = {
  list: (search?: string) =>
    api<PageResponse<Item>>(`/api/items${search ? `?search=${encodeURIComponent(search)}` : ''}`),
  get: (id: string) => api<Item>(`/api/items/${id}`),
  create: (payload: ItemPayload) => api<Item>('/api/items', { method: 'POST', body: payload }),
  update: (id: string, payload: ItemPayload) =>
    api<Item>(`/api/items/${id}`, { method: 'PUT', body: payload }),
  remove: (id: string) => api<void>(`/api/items/${id}`, { method: 'DELETE' }),
};
