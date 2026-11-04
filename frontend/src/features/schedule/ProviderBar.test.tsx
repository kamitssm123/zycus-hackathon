import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ProviderBar from './ProviderBar';

const CHEN = { id: 'PRV-001', name: 'Dr. Sarah Chen', specialty: 'GENERAL', status: 'BUSY' };
const OKONKWO = {
  id: 'PRV-002',
  name: 'Dr. James Okonkwo',
  specialty: 'SPECIALIST',
  status: 'AVAILABLE',
};

// Two patients booked with Chen; none with Okonkwo.
const APPOINTMENTS = [
  { id: 'APT-001', patientName: 'John', providerId: 'PRV-001', status: 'CONFIRMED' },
  { id: 'APT-002', patientName: 'Emily', providerId: 'PRV-001', status: 'CONFIRMED' },
];

function mockApi() {
  const spy = vi.fn((url: string) =>
    Promise.resolve({
      ok: true,
      status: 200,
      text: () =>
        Promise.resolve(
          JSON.stringify(
            String(url).startsWith('/api/providers')
              ? [CHEN, OKONKWO]
              : String(url).startsWith('/api/appointments')
                ? APPOINTMENTS
                : [],
          ),
        ),
    }),
  );
  vi.stubGlobal('fetch', spy);
  return spy;
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const rowFor = async (name: string) => {
  const heading = await screen.findByText(name);
  return heading.closest('li')!;
};

describe('ProviderBar', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('says how many patients will be rescheduled', async () => {
    mockApi();
    render(<ProviderBar />, { wrapper });

    await userEvent.click(
      within(await rowFor('Dr. Sarah Chen')).getByRole('button', { name: 'Take offline' }),
    );

    expect(
      await screen.findByText(/Rescheduling 2 patients for Dr. Sarah Chen/),
    ).toBeInTheDocument();
  });

  it('explains that nothing happens when the provider has no patients', async () => {
    // Otherwise "I clicked and nothing happened" is indistinguishable from a broken button.
    mockApi();
    render(<ProviderBar />, { wrapper });

    await userEvent.click(
      within(await rowFor('Dr. James Okonkwo')).getByRole('button', { name: 'Take offline' }),
    );

    expect(
      await screen.findByText(/had no booked patients, so nothing needs rescheduling/),
    ).toBeInTheDocument();
  });
});
