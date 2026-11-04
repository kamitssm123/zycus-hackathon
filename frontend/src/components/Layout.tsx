import type { ReactNode } from 'react';
import { env } from '../lib/env';
import { useHealth } from '../hooks/useHealth';

export default function Layout({ children }: { children: ReactNode }) {
  const { data, isError } = useHealth();
  const up = !isError && data?.status === 'UP';

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-4">
          <h1 className="text-lg font-semibold">{env.appName}</h1>
          <span
            className={`rounded-full px-2.5 py-1 text-xs font-medium ${
              up ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
            }`}
          >
            API {up ? 'up' : 'down'}
          </span>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8">{children}</main>
    </div>
  );
}
