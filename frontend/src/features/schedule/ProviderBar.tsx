import type { Provider, ProviderStatus } from '../../api/types';
import { useProviders, useSetProviderStatus } from '../../hooks/useScheduling';

const STATUS_STYLES: Record<ProviderStatus, string> = {
  AVAILABLE: 'bg-emerald-100 text-emerald-800',
  BUSY: 'bg-amber-100 text-amber-800',
  OFFLINE: 'bg-rose-100 text-rose-800',
};

/** Taking a provider offline here is the demo's single human action. */
export default function ProviderBar() {
  const { data: providers } = useProviders();
  const setStatus = useSetProviderStatus();

  return (
    <section className="mb-6">
      <h2 className="mb-2 text-base font-semibold text-slate-900">Providers</h2>
      <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
        {(providers ?? []).map((provider: Provider) => (
          <li key={provider.id} className="flex items-center justify-between px-3 py-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{provider.name}</p>
              <p className="text-xs text-slate-500">
                {provider.id} · {provider.specialty.toLowerCase()}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span
                className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[provider.status]}`}
              >
                {provider.status.toLowerCase()}
              </span>
              <button
                onClick={() =>
                  setStatus.mutate({
                    id: provider.id,
                    status: provider.status === 'OFFLINE' ? 'AVAILABLE' : 'OFFLINE',
                  })
                }
                disabled={setStatus.isPending}
                className="rounded border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              >
                {provider.status === 'OFFLINE' ? 'Bring online' : 'Take offline'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
