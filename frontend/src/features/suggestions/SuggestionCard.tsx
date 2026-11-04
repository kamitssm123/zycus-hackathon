import type { Appointment, Provider, RescheduleSuggestion, Slot } from '../../api/types';
import TriggerBadge from './TriggerBadge';

interface Props {
  suggestion: RescheduleSuggestion;
  appointment?: Appointment;
  slot?: Slot;
  provider?: Provider;
  onDecide: (status: 'ACCEPTED' | 'REJECTED') => void;
  busy: boolean;
}

const TYPE_STYLES: Record<string, string> = {
  URGENT: 'bg-rose-100 text-rose-800',
  FOLLOW_UP: 'bg-violet-100 text-violet-800',
  ROUTINE: 'bg-slate-100 text-slate-700',
};

function formatTime(iso?: string) {
  if (!iso) return 'time unknown';
  return new Date(iso).toLocaleString(undefined, {
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function SuggestionCard({
  suggestion,
  appointment,
  slot,
  provider,
  onDecide,
  busy,
}: Props) {
  const confidencePct = Math.round(suggestion.confidence * 100);

  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <header className="mb-2 flex flex-wrap items-center gap-2">
        <h3 className="font-medium text-slate-900">
          {appointment?.patientName ?? suggestion.appointmentId}
        </h3>
        {appointment && (
          <span
            className={`rounded px-1.5 py-0.5 text-xs font-medium ${
              TYPE_STYLES[appointment.type] ?? TYPE_STYLES.ROUTINE
            }`}
          >
            {appointment.type.replace('_', ' ').toLowerCase()}
          </span>
        )}
        <TriggerBadge reason={suggestion.triggerReason} />
      </header>

      <p className="mb-3 text-sm text-slate-700">{suggestion.reasoning}</p>

      <dl className="mb-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-600">
        <div>
          <dt className="inline font-medium">New time: </dt>
          <dd className="inline">{formatTime(slot?.startTime)}</dd>
        </div>
        <div>
          <dt className="inline font-medium">Doctor: </dt>
          <dd className="inline">{provider?.name ?? 'unchanged'}</dd>
        </div>
      </dl>

      <div className="mb-3 flex items-center gap-2">
        <div
          className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200"
          role="meter"
          aria-label="confidence"
          aria-valuenow={confidencePct}
        >
          <div
            className={`h-full ${confidencePct >= 80 ? 'bg-emerald-500' : confidencePct >= 60 ? 'bg-amber-500' : 'bg-rose-500'}`}
            style={{ width: `${confidencePct}%` }}
          />
        </div>
        <span className="text-xs tabular-nums text-slate-500">{confidencePct}% confident</span>
        <span className="text-xs text-slate-400">via {suggestion.strategyKey}</span>
      </div>

      {suggestion.fallbackUsed && (
        // Structured field, not a string prefix on `reasoning` — so it can be styled and
        // translated, and nobody ends up string-matching on prose.
        <p className="mb-3 rounded bg-amber-50 px-2 py-1 text-xs text-amber-800">
          AI unavailable — this is a rule-based suggestion.
        </p>
      )}

      <div className="flex gap-2">
        <button
          onClick={() => onDecide('ACCEPTED')}
          disabled={busy}
          className="rounded-md bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
        >
          Accept
        </button>
        <button
          onClick={() => onDecide('REJECTED')}
          disabled={busy}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          Reject
        </button>
      </div>
    </article>
  );
}
