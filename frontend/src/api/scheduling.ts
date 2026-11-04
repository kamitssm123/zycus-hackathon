import { api } from './client';
import type {
  Appointment,
  Provider,
  ProviderStatus,
  RescheduleSuggestion,
  SchedulingConfig,
  Slot,
  SlotStatus,
  SuggestionDecision,
  SuggestionStatus,
} from './types';

/** One module per resource. The only place URL strings live. */
export const appointmentsApi = {
  list: (status?: string) =>
    api<Appointment[]>(`/api/appointments${status ? `?status=${status}` : ''}`),
  suggest: (id: string) =>
    api<RescheduleSuggestion>(`/api/appointments/${id}/suggest`, { method: 'POST' }),
};

export const providersApi = {
  list: () => api<Provider[]>('/api/providers'),
  setStatus: (id: string, status: ProviderStatus) =>
    api<Provider>(`/api/providers/${id}/status`, { method: 'PATCH', body: { status } }),
};

export const slotsApi = {
  list: () => api<Slot[]>('/api/slots'),
  setStatus: (id: string, status: SlotStatus) =>
    api<Slot>(`/api/slots/${id}/status`, { method: 'PATCH', body: { status } }),
};

export const suggestionsApi = {
  list: (status?: SuggestionStatus) =>
    api<RescheduleSuggestion[]>(`/api/suggestions${status ? `?status=${status}` : ''}`),
  decide: (id: string, status: 'ACCEPTED' | 'REJECTED') =>
    api<SuggestionDecision>(`/api/suggestions/${id}`, { method: 'PATCH', body: { status } }),
};

export const engineApi = {
  get: () => api<SchedulingConfig>('/api/admin/scheduling'),
  setStrategy: (strategy: string) =>
    api<SchedulingConfig>('/api/admin/scheduling', { method: 'PATCH', body: { strategy } }),
};
