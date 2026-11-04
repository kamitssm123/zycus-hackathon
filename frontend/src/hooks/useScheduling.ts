import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  appointmentsApi,
  engineApi,
  providersApi,
  slotsApi,
  suggestionsApi,
} from '../api/scheduling';
import type { ProviderStatus, SlotStatus } from '../api/types';

const KEYS = {
  suggestions: ['suggestions'] as const,
  appointments: ['appointments'] as const,
  providers: ['providers'] as const,
  slots: ['slots'] as const,
  engine: ['engine'] as const,
};

/**
 * Polls every 2s. This is what makes "automatic" visible: the coordinator takes a provider
 * offline and watches suggestions appear without touching anything. Without polling the demo
 * looks broken and the agentic story is lost along with it.
 */
export function useSuggestions() {
  return useQuery({
    queryKey: KEYS.suggestions,
    queryFn: () => suggestionsApi.list('PENDING'),
    refetchInterval: 2000,
  });
}

export function useAppointments() {
  return useQuery({
    queryKey: KEYS.appointments,
    queryFn: () => appointmentsApi.list(),
    refetchInterval: 2000,
  });
}

export function useProviders() {
  return useQuery({
    queryKey: KEYS.providers,
    queryFn: () => providersApi.list(),
    refetchInterval: 5000,
  });
}

export function useSlots() {
  return useQuery({
    queryKey: KEYS.slots,
    queryFn: () => slotsApi.list(),
    refetchInterval: 5000,
  });
}

export function useEngineConfig() {
  return useQuery({ queryKey: KEYS.engine, queryFn: () => engineApi.get() });
}

/** Everything the agent touches is invalidated together — one source of truth for freshness. */
function useRefreshAll() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: KEYS.suggestions }),
      queryClient.invalidateQueries({ queryKey: KEYS.appointments }),
      queryClient.invalidateQueries({ queryKey: KEYS.slots }),
      queryClient.invalidateQueries({ queryKey: KEYS.providers }),
    ]);
}

export function useDecideSuggestion() {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACCEPTED' | 'REJECTED' }) =>
      suggestionsApi.decide(id, status),
    onSettled: refresh,
  });
}

/**
 * "That one doesn't suit — what else?" Rejects and proposes a different slot in one call, so the
 * coordinator's single real-world action is a single click rather than reject-then-hunt.
 */
export function useSuggestAlternative() {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: (id: string) => suggestionsApi.alternative(id),
    onSettled: refresh,
  });
}

export function useSetProviderStatus() {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: ProviderStatus }) =>
      providersApi.setStatus(id, status),
    onSettled: refresh,
  });
}

export function useSetSlotStatus() {
  const refresh = useRefreshAll();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: SlotStatus }) =>
      slotsApi.setStatus(id, status),
    onSettled: refresh,
  });
}

export function useSetStrategy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (strategy: string) => engineApi.setStrategy(strategy),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEYS.engine }),
  });
}
