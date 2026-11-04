import EngineSwitch from '../features/schedule/EngineSwitch';
import ProviderBar from '../features/schedule/ProviderBar';
import SlotList from '../features/schedule/SlotList';
import SuggestionQueue from '../features/suggestions/SuggestionQueue';

export default function HomePage() {
  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
      <div>
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm text-slate-600">
            Take a provider offline and suggestions appear here on their own.
          </p>
          <EngineSwitch />
        </div>
        <SuggestionQueue />
      </div>
      <aside>
        <ProviderBar />
        <SlotList />
      </aside>
    </div>
  );
}
