import type { TriggerReason } from '../../api/types';

/** Why this suggestion exists — the brief's auto-vs-manual distinction, made visible. */
const LABELS: Record<TriggerReason, { label: string; className: string; title: string }> = {
  PROVIDER_OFFLINE: {
    label: 'Auto · provider offline',
    className: 'bg-amber-100 text-amber-800 ring-amber-200',
    title: 'Created automatically when the provider was marked offline',
  },
  SLOT_CANCELLED: {
    label: 'Auto · slot cancelled',
    className: 'bg-sky-100 text-sky-800 ring-sky-200',
    title: 'Created automatically when the slot was cancelled',
  },
  INITIAL: {
    label: 'Manual request',
    className: 'bg-slate-100 text-slate-700 ring-slate-200',
    title: 'Requested by a coordinator',
  },
};

export default function TriggerBadge({ reason }: { reason: TriggerReason }) {
  const { label, className, title } = LABELS[reason];
  return (
    <span
      title={title}
      className={`rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${className}`}
    >
      {label}
    </span>
  );
}
