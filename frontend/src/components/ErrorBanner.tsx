import { ApiError } from '../api/client';

/** Renders any thrown error consistently, surfacing requestId so logs can be grepped. */
export default function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const isApi = error instanceof ApiError;
  const message = error instanceof Error ? error.message : 'Unexpected error';

  return (
    <div className="mb-4 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
      <p className="font-medium">{message}</p>
      {isApi && error.body.requestId && (
        <p className="mt-1 text-xs opacity-70">request-id: {error.body.requestId}</p>
      )}
      {isApi &&
        error.body.details?.map((d) => (
          <p key={d.field} className="mt-1 text-xs">
            {d.field}: {d.message}
          </p>
        ))}
    </div>
  );
}
