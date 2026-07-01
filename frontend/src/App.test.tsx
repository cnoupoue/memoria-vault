import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MemorySource } from './api/types';
import App from './App';

vi.mock('./api/snapmemoriaApi', () => ({
  createMemorySource: vi.fn(),
  deleteMemorySource: vi.fn(),
  getFlashbacksByDate: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemories: vi.fn(),
  getMemoryDetail: vi.fn(),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  getTimelineMonths: vi.fn(),
  getTimelineYears: vi.fn(),
  getTodayFlashbacks: vi.fn(),
  startMemorySourceScan: vi.fn(),
  SnapmemoriaApiError: class SnapmemoriaApiError extends Error {},
}));

import {
  createMemorySource,
  getMemorySources,
  getTimelineYears,
} from './api/snapmemoriaApi';

const createMemorySourceMock = vi.mocked(createMemorySource);
const getMemorySourcesMock = vi.mocked(getMemorySources);
const getTimelineYearsMock = vi.mocked(getTimelineYears);

beforeEach(() => {
  getTimelineYearsMock.mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
  cleanup();
});

function buildSource(source: Partial<MemorySource> = {}): MemorySource {
  return {
    id: 'source-1',
    name: 'Snapchat USB',
    rootPath: '/Volumes/SNAP/snapchat-memories',
    lastScanAt: null,
    lastScanStatus: 'NOT_SCANNED',
    availabilityStatus: 'AVAILABLE',
    availabilityMessage: 'Source folder is available.',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...source,
  };
}

describe('App onboarding', () => {
  it('renders onboarding when there are no configured sources', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to SnapMemoria'),
    ).toBeInTheDocument();
    expect(screen.getByText('Add your Snapchat export')).toBeInTheDocument();
  });

  it('does not show the archive as a broken empty state when no sources exist', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to SnapMemoria'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('No Memories found for this period.'),
    ).not.toBeInTheDocument();
    expect(getTimelineYearsMock).not.toHaveBeenCalled();
  });

  it('explains that files remain local', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Your files stay private.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Nothing is uploaded.')).toBeInTheDocument();
  });

  it('shows the folder structure example', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText(/snapchat-memories\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 2\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 3\//)).toBeInTheDocument();
  });

  it('opens the source creation flow from the primary action', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    await user.click(
      await screen.findByRole('button', { name: 'Add your Snapchat export' }),
    );

    expect(
      screen.getByRole('heading', { name: 'Settings' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Source name')).toHaveFocus();
  });

  it('hides onboarding after a source is added', async () => {
    const user = userEvent.setup();
    const source = buildSource();

    getMemorySourcesMock.mockResolvedValue([]);
    createMemorySourceMock.mockResolvedValue(source);

    render(<App />);

    await user.click(
      await screen.findByRole('button', { name: 'Add your Snapchat export' }),
    );
    await user.type(screen.getByLabelText('Source name'), 'Snapchat USB');
    await user.type(
      screen.getByLabelText('Export folder path'),
      '/Volumes/SNAP/snapchat-memories',
    );
    await user.click(screen.getByRole('button', { name: 'Add source' }));

    await waitFor(() => {
      expect(
        screen.queryByText('Welcome to SnapMemoria'),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByText('Your source was added.')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Scan source' }),
    ).toBeInTheDocument();
  });

  it('renders source loading errors safely', async () => {
    getMemorySourcesMock.mockRejectedValue(
      new Error('/Users/private/path failed'),
    );

    render(<App />);

    expect(
      await screen.findByText(
        'Could not load setup status. Check that the backend is running.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('/Users/private/path failed'),
    ).not.toBeInTheDocument();
  });
});
