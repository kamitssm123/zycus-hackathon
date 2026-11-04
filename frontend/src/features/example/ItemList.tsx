import { useState } from 'react';
import ErrorBanner from '../../components/ErrorBanner';
import Spinner from '../../components/Spinner';
import { useCreateItem, useDeleteItem, useItems } from '../../hooks/useItems';

/**
 * Reference feature component — copy this shape for real features, then delete the folder.
 * Pattern: hooks for data, local state only for form input, no fetch calls inline.
 */
export default function ItemList() {
  const [name, setName] = useState('');
  const { data, isLoading, error } = useItems();
  const createItem = useCreateItem();
  const deleteItem = useDeleteItem();

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    createItem.mutate({ name: name.trim() }, { onSuccess: () => setName('') });
  };

  return (
    <section>
      <form onSubmit={submit} className="mb-6 flex gap-2">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Add an item…"
          className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={createItem.isPending}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {createItem.isPending ? 'Adding…' : 'Add'}
        </button>
      </form>

      <ErrorBanner error={error ?? createItem.error ?? deleteItem.error} />

      {isLoading && <Spinner />}

      {data && data.items.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-500">Nothing here yet.</p>
      )}

      <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 bg-white">
        {data?.items.map((item) => (
          <li key={item.id} className="flex items-center justify-between px-3 py-2 text-sm">
            <span>{item.name}</span>
            <button
              onClick={() => deleteItem.mutate(item.id)}
              className="text-xs text-slate-500 hover:text-rose-600"
            >
              delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
