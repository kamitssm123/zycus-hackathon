import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

interface Health {
  status: string;
  service: string;
  time: string;
}

/** Drives the header badge — instant visual proof the API container is reachable. */
export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => api<Health>('/api/health'),
    refetchInterval: 15_000,
  });
}
