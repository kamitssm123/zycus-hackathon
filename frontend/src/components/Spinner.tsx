export default function Spinner({ label = 'Loading…' }: { label?: string }) {
  return <p className="py-6 text-center text-sm text-slate-500">{label}</p>;
}
