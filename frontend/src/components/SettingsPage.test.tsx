import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MemorySource } from '../api/types';
import { SettingsPage } from './SettingsPage';

vi.mock('../api/snapmemoriaApi', () => ({
  createMemorySource: vi.fn(),
  deleteMemorySource: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  startMemorySourceScan: vi.fn(),
  SnapmemoriaApiError: class SnapmemoriaApiError extends Error {},
}));

import {
  getMemorySourceAvailability,
  getMemorySources,
  startMemorySourceScan,
} from '../api/snapmemoriaApi';

const getMemorySourcesMock = vi.mocked(getMemorySources);
const getMemorySourceAvailabilityMock = vi.mocked(getMemorySourceAvailability);
const startMemorySourceScanMock = vi.mocked(startMemorySourceScan);

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function buildSource(source: Partial<MemorySource>): MemorySource {
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

describe('SettingsPage', () => {
  it('renders available source status', async () => {
    getMemorySourcesMock.mockResolvedValue([buildSource({})]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(await screen.findByText('Available')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan source' })).toBeEnabled();
  });

  it('renders unavailable source status', async () => {
    getMemorySourcesMock.mockResolvedValue([
      buildSource({
        availabilityStatus: 'UNAVAILABLE',
        availabilityMessage:
          'Connect the drive containing this source, then refresh its status.',
      }),
    ]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder moved or missing'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Connect the drive containing this source, then refresh its status.',
      ),
    ).toBeInTheDocument();
  });

  it('disables scan when source is unavailable', async () => {
    getMemorySourcesMock.mockResolvedValue([
      buildSource({ availabilityStatus: 'NOT_READABLE' }),
    ]);

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder is not readable'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan source' })).toBeDisabled();
    expect(startMemorySourceScanMock).not.toHaveBeenCalled();
  });

  it('refreshes a single source availability status', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([
      buildSource({
        availabilityStatus: 'UNAVAILABLE',
        availabilityMessage:
          'Connect the drive containing this source, then refresh its status.',
      }),
    ]);
    getMemorySourceAvailabilityMock.mockResolvedValue({
      availabilityStatus: 'AVAILABLE',
      availabilityMessage: 'Source folder is available.',
    });

    render(<SettingsPage onSourceScanned={vi.fn()} />);

    expect(
      await screen.findByText('Folder moved or missing'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Refresh status' }));

    await waitFor(() => {
      expect(screen.getByText('Available')).toBeInTheDocument();
    });
    expect(getMemorySourceAvailabilityMock).toHaveBeenCalledWith('source-1');
  });
});
