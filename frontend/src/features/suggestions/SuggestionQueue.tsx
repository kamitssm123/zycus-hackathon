import ErrorBanner from '../../components/ErrorBanner';
import Spinner from '../../components/Spinner';
import {
  useAppointments,
  useDecideSuggestion,
  useProviders,
  useSlots,
  useSuggestions,
} from '../../hooks/useScheduling';
import SuggestionCard from './SuggestionCard';

export default function SuggestionQueue() {
  const { data: suggestions, isLoading, error } = useSuggestions();
  const { data: appointments } = useAppointments();
  const { data: slots } = useSlots();
  const { data: providers } = useProviders();
  const decide = useDecideSuggestion();

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

      <ErrorBanner error={error ?? decide.error} />

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
              busy={decide.isPending}
              onDecide={(status) => decide.mutate({ id: suggestion.id, status })}
            />
          );
        })}
      </div>
    </section>
  );
}
