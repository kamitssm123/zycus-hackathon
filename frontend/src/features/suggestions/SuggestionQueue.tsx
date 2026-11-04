import ErrorBanner from '../../components/ErrorBanner';
import Spinner from '../../components/Spinner';
import { useState } from 'react';
import {
  useAppointments,
  useDecideSuggestion,
  useProviders,
  useSlots,
  useSuggestAlternative,
  useSuggestions,
} from '../../hooks/useScheduling';
import SuggestionCard from './SuggestionCard';

export default function SuggestionQueue() {
  const { data: suggestions, isLoading, error } = useSuggestions();
  const { data: appointments } = useAppointments();
  const { data: slots } = useSlots();
  const { data: providers } = useProviders();
  const decide = useDecideSuggestion();
  const suggestAlternative = useSuggestAlternative();
  const [exhausted, setExhausted] = useState<string | null>(null);

  // Per-card, not per-mutation. A single `isPending` flag is shared by every card, so acting on
  // one suggestion would disable the controls on all of them — which reads as the UI freezing,
  // and blocks a coordinator who is working through several patients.
  const [pendingIds, setPendingIds] = useState<string[]>([]);
  const startWork = (id: string) => setPendingIds((ids) => [...ids, id]);
  const finishWork = (id: string) => setPendingIds((ids) => ids.filter((x) => x !== id));

  const appointmentById = new Map((appointments ?? []).map((a) => [a.id, a]));
  const slotById = new Map((slots ?? []).map((s) => [s.id, s]));
  const providerById = new Map((providers ?? []).map((p) => [p.id, p]));

  return (
    <section>
      <div className="mb-3 flex items-baseline justify-between">
        <h2 className="text-base font-semibold text-slate-900">Awaiting your call</h2>
        <span className="text-xs text-slate-500">
          {suggestions?.length ?? 0} pending · updating live
        </span>
      </div>

      <ErrorBanner error={error ?? decide.error ?? suggestAlternative.error} />

      {exhausted && (
        // The rejection succeeded; there was simply nothing else to offer. Saying so explicitly
        // matters — otherwise the card vanishes and the coordinator cannot tell whether the
        // system is still working on it or has given up.
        <p className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          No other suitable slot is available for {exhausted}. They keep their original appointment
          — try freeing a slot, or bring a provider back online.
        </p>
      )}

      {isLoading && <Spinner label="Loading suggestions…" />}

      {suggestions?.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-300 py-8 text-center text-sm text-slate-500">
          Nothing needs rebooking. Take a provider offline to see the engine react.
        </p>
      )}

      <div className="space-y-3">
        {suggestions?.map((suggestion) => {
          const appointment = appointmentById.get(suggestion.appointmentId);
          const slot = slotById.get(suggestion.recommendedSlotId);
          return (
            <SuggestionCard
              key={suggestion.id}
              suggestion={suggestion}
              appointment={appointment}
              slot={slot}
              provider={providerById.get(suggestion.alternateProviderId ?? slot?.providerId ?? '')}
              busy={pendingIds.includes(suggestion.id)}
              onDecide={(status) => {
                setExhausted(null);
                startWork(suggestion.id);
                decide.mutate(
                  { id: suggestion.id, status },
                  { onSettled: () => finishWork(suggestion.id) },
                );
              }}
              onSuggestAlternative={() => {
                startWork(suggestion.id);
                suggestAlternative.mutate(suggestion.id, {
                  onSuccess: (result) =>
                    setExhausted(
                      result.alternative
                        ? null
                        : (appointment?.patientName ?? suggestion.appointmentId),
                    ),
                  onSettled: () => finishWork(suggestion.id),
                });
              }}
            />
          );
        })}
      </div>
    </section>
  );
}
