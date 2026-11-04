import type { Slot, SlotStatus } from '../../api/types';
import { useProviders, useSetSlotStatus, useSlots } from '../../hooks/useScheduling';

const STATUS_STYLES: Record<SlotStatus, string> = {
  OPEN: 'bg-emerald-50 text-emerald-700',
  BOOKED: 'bg-slate-100 text-slate-700',
  CANCELLED: 'bg-rose-50 text-rose-700',
};

export default function SlotList() {
  const { data: slots } = useSlots();
  const { data: providers } = useProviders();
  const setStatus = useSetSlotStatus();
  const providerById = new Map((providers ?? []).map((p) => [p.id, p]));

  return (
    <section>
      <h2 className="mb-2 text-base font-semibold text-slate-900">Today&rsquo;s slots</h2>
      <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 bg-white">
        {(slots ?? []).map((slot: Slot) => {
          const provider = providerById.get(slot.providerId);
          // A slot belonging to an offline provider is not bookable — the API refuses it with
          // 409 "Provider is offline". Showing it as "open" would be the UI telling the
          // coordinator something the backend will not honour. The slot's own status stays
          // OPEN on purpose: the slot was not cancelled, the doctor is away, and when they
          // come back it is bookable again. Availability is derived, not denormalised.
          const unavailable = provider?.status === 'OFFLINE' && slot.status === 'OPEN';
          return (
            <li
              key={slot.id}
              className={`flex items-center justify-between px-3 py-2 text-sm ${unavailable ? 'opacity-60' : ''}`}
            >
              <div className="min-w-0">
                <span className="font-medium text-slate-900">
                  {new Date(slot.startTime).toLocaleTimeString(undefined, {
                    hour: 'numeric',
                    minute: '2-digit',
                  })}
                </span>
                <span className="ml-2 text-xs text-slate-500">
                  {provider?.name ?? slot.providerId} · {slot.id}
                </span>
              </div>
              <div className="flex items-center gap-2">
                {unavailable ? (
                  <span
                    title="This provider is offline, so the slot cannot be booked"
                    className="rounded bg-amber-50 px-2 py-0.5 text-xs text-amber-700"
                  >
                    provider offline
                  </span>
                ) : (
                  <span className={`rounded px-2 py-0.5 text-xs ${STATUS_STYLES[slot.status]}`}>
                    {slot.status.toLowerCase()}
                  </span>
                )}
                {slot.status !== 'CANCELLED' && (
                  <button
                    onClick={() => setStatus.mutate({ id: slot.id, status: 'CANCELLED' })}
                    disabled={setStatus.isPending}
                    className="text-xs text-slate-500 hover:text-rose-600 disabled:opacity-50"
                  >
                    cancel
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
