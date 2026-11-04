import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SuggestionQueue from './SuggestionQueue';

const SUGGESTION = {
  id: 'SGT-001',
  appointmentId: 'APT-001',
  recommendedSlotId: 'SLT-005',
  alternateProviderId: 'PRV-004',
  confidence: 0.88,
  reasoning: 'Dr. Chen is unavailable, so we suggest Dr. Verma at 1:30 PM.',
  status: 'PENDING',
  triggerReason: 'PROVIDER_OFFLINE',
  strategyKey: 'ai',
  fallbackUsed: false,
  createdAt: '2026-11-04T11:00:00Z',
};

const SUGGESTION_2 = {
  ...{
    id: 'SGT-002',
    appointmentId: 'APT-002',
    recommendedSlotId: 'SLT-007',
    alternateProviderId: 'PRV-004',
    confidence: 0.75,
    reasoning: 'Dr. Chen is unavailable, so we suggest Dr. Verma at 2:30 PM.',
    status: 'PENDING',
    triggerReason: 'PROVIDER_OFFLINE',
    strategyKey: 'ai',
    fallbackUsed: false,
    createdAt: '2026-11-04T11:00:00Z',
  },
};

const APPOINTMENT_2 = {
  id: 'APT-002',
  patientName: 'Emily Watson',
  type: 'FOLLOW_UP',
  providerId: 'PRV-001',
  slotId: 'SLT-002',
  status: 'RESCHEDULE_PENDING',
  updatedAt: '2026-11-04T11:00:00Z',
};

const APPOINTMENT = {
  id: 'APT-001',
  patientName: 'John Martinez',
  type: 'ROUTINE',
  providerId: 'PRV-001',
  slotId: 'SLT-001',
  status: 'RESCHEDULE_PENDING',
  updatedAt: '2026-11-04T11:00:00Z',
};

const SLOT = {
  id: 'SLT-005',
  providerId: 'PRV-004',
  startTime: '2026-11-04T13:30:00Z',
  durationMinutes: 30,
  status: 'OPEN',
};

const PROVIDER = {
  id: 'PRV-004',
  name: 'Dr. Rahul Verma',
  specialty: 'GENERAL',
  status: 'AVAILABLE',
};

/** Routes each endpoint to canned data; `overrides` lets a test change one response. */
function mockApi(overrides: Record<string, { status: number; body: unknown }> = {}) {
  const spy = vi.fn((url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url.split('?')[0]}`;
    const override = overrides[key];
    const body = override
      ? override.body
      : url.startsWith('/api/suggestions')
        ? [SUGGESTION]
        : url.startsWith('/api/appointments')
          ? [APPOINTMENT]
          : url.startsWith('/api/slots')
            ? [SLOT]
            : url.startsWith('/api/providers')
              ? [PROVIDER]
              : [];
    const status = override?.status ?? 200;
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      text: () => Promise.resolve(JSON.stringify(body)),
    });
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('SuggestionQueue', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders the patient, reasoning, confidence and trigger badge', async () => {
    mockApi();
    render(<SuggestionQueue />, { wrapper });

    expect(await screen.findByText('John Martinez')).toBeInTheDocument();
    expect(screen.getByText(/Dr. Chen is unavailable/)).toBeInTheDocument();
    expect(screen.getByText('88% confident')).toBeInTheDocument();
    // The auto-vs-manual distinction the brief asks for.
    expect(screen.getByText('Auto · provider offline')).toBeInTheDocument();
  });

  it('accepting issues a PATCH with the accepted status', async () => {
    const spy = mockApi();
    render(<SuggestionQueue />, { wrapper });
    await screen.findByText('John Martinez');

    await userEvent.click(screen.getByRole('button', { name: 'Accept' }));

    await waitFor(() => {
      const patch = spy.mock.calls.find(
        ([url, init]) =>
          init?.method === 'PATCH' && String(url).includes('/api/suggestions/SGT-001'),
      );
      expect(patch).toBeDefined();
      expect(JSON.parse(String(patch![1]!.body))).toEqual({ status: 'ACCEPTED' });
    });
  });

  it('shows the error banner with the requestId when the slot was taken first', async () => {
    mockApi({
      'PATCH /api/suggestions/SGT-001': {
        status: 409,
        body: {
          code: 'CONFLICT',
          message: 'Slot SLT-005 is no longer available (BOOKED)',
          requestId: 'a1b2c3d4',
        },
      },
    });
    render(<SuggestionQueue />, { wrapper });
    await screen.findByText('John Martinez');

    await userEvent.click(screen.getByRole('button', { name: 'Accept' }));

    expect(await screen.findByText(/no longer available/)).toBeInTheDocument();
    expect(screen.getByText(/a1b2c3d4/)).toBeInTheDocument();
  });

  it('flags a rule-based fallback as a structured state, not buried in the reasoning', async () => {
    mockApi({
      'GET /api/suggestions': {
        status: 200,
        body: [{ ...SUGGESTION, fallbackUsed: true, strategyKey: 'rule' }],
      },
    });
    render(<SuggestionQueue />, { wrapper });

    expect(await screen.findByText(/AI unavailable/)).toBeInTheDocument();
  });

  it('suggest another posts to the alternative endpoint', async () => {
    const spy = mockApi({
      'POST /api/suggestions/SGT-001/alternative': {
        status: 200,
        body: {
          rejected: { suggestionId: 'SGT-001', suggestionStatus: 'REJECTED' },
          alternative: { ...SUGGESTION, id: 'SGT-002', recommendedSlotId: 'SLT-007' },
        },
      },
    });
    render(<SuggestionQueue />, { wrapper });
    await screen.findByText('John Martinez');

    await userEvent.click(screen.getByRole('button', { name: 'Suggest another' }));

    await waitFor(() => {
      expect(
        spy.mock.calls.find(
          ([url, init]) =>
            init?.method === 'POST' && String(url) === '/api/suggestions/SGT-001/alternative',
        ),
      ).toBeDefined();
    });
  });

  it('says so plainly when there is nothing else to offer', async () => {
    mockApi({
      'POST /api/suggestions/SGT-001/alternative': {
        status: 200,
        // The rejection succeeded; there is simply no alternative. Not an error.
        body: { rejected: { suggestionId: 'SGT-001', suggestionStatus: 'REJECTED' } },
      },
    });
    render(<SuggestionQueue />, { wrapper });
    await screen.findByText('John Martinez');

    await userEvent.click(screen.getByRole('button', { name: 'Suggest another' }));

    expect(await screen.findByText(/No other suitable slot is available/)).toBeInTheDocument();
    expect(screen.getByText(/keep their original appointment/)).toBeInTheDocument();
  });

  it('only disables the card being acted on, not every card', async () => {
    // Regression: `busy` was a single shared mutation flag, so clicking "Suggest another" on one
    // patient disabled Accept/Reject on every other patient — which reads as the UI freezing and
    // blocks a coordinator working through a list.
    let resolveAlternative: (v: unknown) => void = () => {};
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        if (init?.method === 'POST' && String(url).includes('/alternative')) {
          // Hold the request open so the pending state is observable.
          return new Promise((resolve) => {
            resolveAlternative = resolve;
          });
        }
        const body = String(url).startsWith('/api/suggestions')
          ? [SUGGESTION, SUGGESTION_2]
          : String(url).startsWith('/api/appointments')
            ? [APPOINTMENT, APPOINTMENT_2]
            : String(url).startsWith('/api/slots')
              ? [SLOT]
              : [PROVIDER];
        return Promise.resolve({
          ok: true,
          status: 200,
          text: () => Promise.resolve(JSON.stringify(body)),
        });
      }),
    );

    render(<SuggestionQueue />, { wrapper });
    await screen.findByText('John Martinez');
    await screen.findByText('Emily Watson');

    const cards = screen.getAllByRole('article');
    const johnCard = cards.find((c) => c.textContent?.includes('John Martinez'))!;
    const emilyCard = cards.find((c) => c.textContent?.includes('Emily Watson'))!;

    await userEvent.click(within(johnCard).getByRole('button', { name: 'Suggest another' }));

    // John's card is working...
    await waitFor(() =>
      expect(within(johnCard).getByRole('button', { name: 'Accept' })).toBeDisabled(),
    );
    // ...Emily's is still fully usable.
    expect(within(emilyCard).getByRole('button', { name: 'Accept' })).toBeEnabled();
    expect(within(emilyCard).getByRole('button', { name: 'Suggest another' })).toBeEnabled();
    expect(within(emilyCard).getByRole('button', { name: 'Reject' })).toBeEnabled();

    resolveAlternative({
      ok: true,
      status: 200,
      text: () => Promise.resolve(JSON.stringify({ rejected: {}, alternative: SUGGESTION_2 })),
    });
  });

  it('tells the coordinator what to do when the queue is empty', async () => {
    mockApi({ 'GET /api/suggestions': { status: 200, body: [] } });
    render(<SuggestionQueue />, { wrapper });

    expect(await screen.findByText(/Nothing needs rebooking/)).toBeInTheDocument();
  });
});
