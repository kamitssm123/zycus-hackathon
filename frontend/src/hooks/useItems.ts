import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { itemsApi } from '../api/items';
import type { ItemPayload } from '../api/types';

const ITEMS_KEY = ['items'] as const;

export function useItems(search?: string) {
  return useQuery({
    queryKey: [...ITEMS_KEY, search ?? ''],
    queryFn: () => itemsApi.list(search),
  });
}

export function useCreateItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ItemPayload) => itemsApi.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ITEMS_KEY }),
  });
}

export function useDeleteItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => itemsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ITEMS_KEY }),
  });
}
