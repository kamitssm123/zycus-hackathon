import { useEngineConfig, useSetStrategy } from '../../hooks/useScheduling';

/** The visible half of T-2: flip the strategy live, with no restart. */
export default function EngineSwitch() {
  const { data: config } = useEngineConfig();
  const setStrategy = useSetStrategy();
  if (!config) return null;

  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="text-slate-500">engine</span>
      {config.availableStrategies.map((strategy) => (
        <button
          key={strategy}
          onClick={() => setStrategy.mutate(strategy)}
          disabled={setStrategy.isPending}
          className={`rounded px-2 py-1 font-medium ${
            config.strategy === strategy
              ? 'bg-slate-900 text-white'
              : 'border border-slate-300 text-slate-600 hover:bg-slate-50'
          }`}
        >
          {strategy}
        </button>
      ))}
    </div>
  );
}
