import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SlotList from './SlotList';

const SLOT = {
  id: 'SLT-005',
  providerId: 'PRV-004',
  startTime: '2026-11-04T20:00:00Z',
  durationMinutes: 30,
  status: 'OPEN',
};

function mockApi(providerStatus: string) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) =>
      Promise.resolve({
        ok: true,
        status: 200,
        text: () =>
          Promise.resolve(
            JSON.stringify(
              url.startsWith('/api/slots')
                ? [SLOT]
                : [
                    {
                      id: 'PRV-004',
                      name: 'Dr. Rahul Verma',
                      specialty: 'GENERAL',
                      status: providerStatus,
                    },
                  ],
            ),
          ),
      }),
    ),
  );
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('SlotList', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows an open slot as open when its provider is available', async () => {
    mockApi('AVAILABLE');
    render(<SlotList />, { wrapper });
    expect(await screen.findByText('open')).toBeInTheDocument();
  });

  it('does not advertise an open slot as bookable when its provider is offline', async () => {
    // The API refuses this booking with 409 "Provider is offline", so showing "open" would be
    // the UI promising something the backend will not honour.
    mockApi('OFFLINE');
    render(<SlotList />, { wrapper });

    expect(await screen.findByText('provider offline')).toBeInTheDocument();
    expect(screen.queryByText('open')).not.toBeInTheDocument();
  });
});
