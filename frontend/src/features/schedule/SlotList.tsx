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
        {(slots ?? []).map((slot: Slot) => (
          <li key={slot.id} className="flex items-center justify-between px-3 py-2 text-sm">
            <div className="min-w-0">
              <span className="font-medium text-slate-900">
                {new Date(slot.startTime).toLocaleTimeString(undefined, {
                  hour: 'numeric',
                  minute: '2-digit',
                })}
              </span>
              <span className="ml-2 text-xs text-slate-500">
                {providerById.get(slot.providerId)?.name ?? slot.providerId} · {slot.id}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className={`rounded px-2 py-0.5 text-xs ${STATUS_STYLES[slot.status]}`}>
                {slot.status.toLowerCase()}
              </span>
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
        ))}
      </ul>
    </section>
  );
}
