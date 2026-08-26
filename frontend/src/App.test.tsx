import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  FlashbackResponse,
  Memory,
  MemoryDetail,
  MemorySortOrder,
  MemorySource,
} from './api/types';
import App from './App';

vi.mock('./api/memoriaVaultApi', () => ({
  addMemoryFavorite: vi.fn(),
  createMemorySource: vi.fn(),
  deleteMemory: vi.fn(),
  deleteMemorySource: vi.fn(),
  exportMemorySourceFavoritesBackup: vi.fn(),
  getFavoriteMemories: vi.fn(),
  getFlashbacksByDate: vi.fn(),
  getDiagnostics: vi.fn(),
  getLatestMemorySourceScan: vi.fn().mockRejectedValue(new Error('No scan')),
  getMemories: vi.fn(),
  getMemoryDetail: vi.fn(),
  getMemoryScanJob: vi.fn(),
  getMemorySourceAvailability: vi.fn(),
  getMemorySources: vi.fn(),
  previewMemorySourceFavoritesRestore: vi.fn(),
  resetApplicationData: vi.fn(),
  restoreMemorySourceFavoritesBackup: vi.fn(),
  selectMemorySourceFolder: vi.fn(),
  getTimelineMonths: vi.fn(),
  getTimelineYears: vi.fn(),
  getTodayFlashbacks: vi.fn(),
  removeMemoryFavorite: vi.fn(),
  startMemorySourceScan: vi.fn(),
  MemoriaVaultApiError: class MemoriaVaultApiError extends Error {},
}));

import {
  addMemoryFavorite,
  createMemorySource,
  deleteMemory,
  getFavoriteMemories,
  getTodayFlashbacks,
  getDiagnostics,
  getMemories,
  getMemoryDetail,
  getMemorySources,
  getTimelineMonths,
  resetApplicationData,
  selectMemorySourceFolder,
  startMemorySourceScan,
  getTimelineYears,
  removeMemoryFavorite,
} from './api/memoriaVaultApi';

const addMemoryFavoriteMock = vi.mocked(addMemoryFavorite);
const createMemorySourceMock = vi.mocked(createMemorySource);
const deleteMemoryMock = vi.mocked(deleteMemory);
const getFavoriteMemoriesMock = vi.mocked(getFavoriteMemories);
const getTodayFlashbacksMock = vi.mocked(getTodayFlashbacks);
const getDiagnosticsMock = vi.mocked(getDiagnostics);
const getMemoriesMock = vi.mocked(getMemories);
const getMemoryDetailMock = vi.mocked(getMemoryDetail);
const getMemorySourcesMock = vi.mocked(getMemorySources);
const getTimelineMonthsMock = vi.mocked(getTimelineMonths);
const resetApplicationDataMock = vi.mocked(resetApplicationData);
const selectMemorySourceFolderMock = vi.mocked(selectMemorySourceFolder);
const startMemorySourceScanMock = vi.mocked(startMemorySourceScan);
const getTimelineYearsMock = vi.mocked(getTimelineYears);
const removeMemoryFavoriteMock = vi.mocked(removeMemoryFavorite);

beforeEach(() => {
  window.localStorage.clear();
  getDiagnosticsMock.mockResolvedValue({
    appVersion: '0.1.0',
    platform: null,
    videoPreviews: {
      available: true,
      source: 'BUNDLED',
      message: 'Using bundled FFmpeg.',
    },
    sources: {
      configured: 0,
      available: 0,
      unavailable: 0,
    },
    database: {
      status: 'READY',
    },
  });
  getMemoriesMock.mockResolvedValue({
    content: [],
    page: 0,
    size: 48,
    totalElements: 0,
    totalPages: 0,
  });
  getFavoriteMemoriesMock.mockResolvedValue({
    content: [],
    page: 0,
    size: 48,
    totalElements: 0,
    totalPages: 0,
  });
  getTodayFlashbacksMock.mockResolvedValue({
    date: '2026-07-18',
    memories: [],
  });
  getMemoryDetailMock.mockResolvedValue({
    id: 'memory-video',
    capturedAt: '2026-01-01',
    mediaType: 'VIDEO',
    hasOverlay: false,
    fileSizeBytes: 1024,
    lastModifiedAt: '2026-01-01T00:00:00Z',
    mediaUrl: '/api/memories/memory-video/media',
    overlayUrl: null,
    isFavorite: false,
    favoritedAt: null,
  });
  addMemoryFavoriteMock.mockResolvedValue({
    id: 'memory-video',
    capturedAt: '2026-01-01',
    mediaType: 'VIDEO',
    hasOverlay: false,
    fileSizeBytes: 1024,
    lastModifiedAt: '2026-01-01T00:00:00Z',
    thumbnailUrl: '/api/memories/memory-video/thumbnail',
    isFavorite: true,
    favoritedAt: '2026-07-18T10:15:30Z',
  });
  removeMemoryFavoriteMock.mockResolvedValue({
    id: 'memory-video',
    capturedAt: '2026-01-01',
    mediaType: 'VIDEO',
    hasOverlay: false,
    fileSizeBytes: 1024,
    lastModifiedAt: '2026-01-01T00:00:00Z',
    thumbnailUrl: '/api/memories/memory-video/thumbnail',
    isFavorite: false,
    favoritedAt: null,
  });
  deleteMemoryMock.mockResolvedValue(undefined);
  getTimelineMonthsMock.mockResolvedValue([]);
  getTimelineYearsMock.mockResolvedValue([]);
  selectMemorySourceFolderMock.mockResolvedValue({
    selected: false,
    path: null,
    name: null,
  });
  startMemorySourceScanMock.mockResolvedValue({
    id: 'scan-1',
    sourceId: 'source-1',
    status: 'RUNNING',
    totalFiles: 0,
    filesProcessed: 0,
    mainImages: 0,
    mainVideos: 0,
    overlays: 0,
    indexedMemories: 0,
    attachedOverlays: 0,
    unmatchedOverlays: 0,
    unsupportedFiles: 0,
    unreadableFiles: 0,
    errorMessage: null,
    startedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    updatedAt: '2026-01-01T00:00:00Z',
  });
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
    favoriteCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...source,
  };
}

function buildMemory(
  memory: Partial<Memory> & Pick<Memory, 'id' | 'capturedAt'>,
): Memory {
  return {
    mediaType: 'IMAGE',
    hasOverlay: false,
    fileSizeBytes: 1024,
    lastModifiedAt: `${memory.capturedAt}T00:00:00Z`,
    thumbnailUrl: `/api/memories/${memory.id}/thumbnail`,
    isFavorite: false,
    favoritedAt: null,
    ...memory,
  };
}

function buildMemoryDetail(memory: Memory): MemoryDetail {
  return {
    id: memory.id,
    capturedAt: memory.capturedAt,
    mediaType: memory.mediaType,
    hasOverlay: memory.hasOverlay,
    fileSizeBytes: memory.fileSizeBytes,
    lastModifiedAt: memory.lastModifiedAt,
    mediaUrl: `/api/memories/${memory.id}/media`,
    overlayUrl: null,
    isFavorite: memory.isFavorite,
    favoritedAt: memory.favoritedAt,
  };
}

function mockMemoryDetails(memories: Memory[]) {
  getMemoryDetailMock.mockImplementation((memoryId) => {
    const memory = memories.find((item) => item.id === memoryId);

    if (!memory) {
      return Promise.reject(new Error(`Missing memory fixture: ${memoryId}`));
    }

    return Promise.resolve(buildMemoryDetail(memory));
  });
}

function mockArchiveMemories(memories: Memory[]) {
  getMemorySourcesMock.mockResolvedValue([buildSource()]);
  getTimelineYearsMock.mockResolvedValue([
    { year: 2026, memoryCount: memories.length },
  ]);
  getMemoriesMock.mockResolvedValue({
    content: memories,
    page: 0,
    size: 48,
    totalElements: memories.length,
    totalPages: memories.length === 0 ? 0 : 1,
  });
  mockMemoryDetails(memories);
}

function mockSortableArchiveMemories(
  newestFirstMemories: Memory[],
  oldestFirstMemories: Memory[],
) {
  getMemorySourcesMock.mockResolvedValue([buildSource()]);
  getTimelineYearsMock.mockResolvedValue([
    { year: 2026, memoryCount: newestFirstMemories.length },
  ]);
  getMemoriesMock.mockImplementation(
    (
      _year?: number,
      _month?: number,
      page = 0,
      size = 48,
      sortOrder: MemorySortOrder = 'NEWEST_FIRST',
    ) => {
      const content =
        sortOrder === 'OLDEST_FIRST'
          ? oldestFirstMemories
          : newestFirstMemories;

      return Promise.resolve({
        content,
        page,
        size,
        totalElements: content.length,
        totalPages: content.length === 0 ? 0 : 1,
      });
    },
  );
  mockMemoryDetails([...newestFirstMemories, ...oldestFirstMemories]);
}

function memoryCardDates() {
  return screen
    .getAllByRole('button', { name: /Open Memory from/ })
    .map((button) =>
      button.getAttribute('aria-label')?.replace('Open Memory from ', ''),
    );
}

function viewer() {
  return within(screen.getByRole('dialog'));
}

describe('App onboarding', () => {
  it('sets the public browser title', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    await screen.findByText('Welcome to Memoria Vault');

    expect(document.title).toBe('Memoria Vault');
  });

  it('renders onboarding when there are no configured sources', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to Memoria Vault'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Choose exported archive folder'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/MemoriaVault/)).not.toBeInTheDocument();
    expect(screen.queryByText(/official Snapchat/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/affiliated with/i)).not.toBeInTheDocument();
  });

  it('does not show the archive as a broken empty state when no sources exist', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('Welcome to Memoria Vault'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('No Memories found for this period.'),
    ).not.toBeInTheDocument();
    expect(getTimelineYearsMock).not.toHaveBeenCalled();
  });

  it('explains that files remain local', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText('Local only.')).toBeInTheDocument();
    expect(screen.getByText('Nothing is uploaded.')).toBeInTheDocument();
  });

  it('shows the independence disclaimer and descriptive compatibility wording', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText(
        'This application is an independent, open-source local tool and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Snap Inc. or Snapchat.',
      ),
    ).toBeInTheDocument();
  });

  it('shows the folder structure example', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText(/exported-archive\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 2\//)).toBeInTheDocument();
    expect(screen.getByText(/memories 3\//)).toBeInTheDocument();
  });

  it('opens the folder selection flow from the primary action', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([]);
    selectMemorySourceFolderMock.mockResolvedValue({
      selected: true,
      path: '/Volumes/SNAPCHAT/snapchat-memories',
      name: 'snapchat-memories',
    });

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );

    expect(
      screen.getByRole('heading', { name: 'Settings' }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(selectMemorySourceFolderMock).toHaveBeenCalled();
    });
    expect(screen.getByLabelText('Source name')).toHaveValue(
      'snapchat-memories',
    );
    expect(screen.getByLabelText('Folder path')).toHaveValue(
      '/Volumes/SNAPCHAT/snapchat-memories',
    );
  });

  it('hides onboarding after a source is added', async () => {
    const user = userEvent.setup();
    const source = buildSource();

    getMemorySourcesMock.mockResolvedValue([]);
    createMemorySourceMock.mockResolvedValue(source);

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Choose exported archive folder',
      }),
    );
    await user.type(screen.getByLabelText('Source name'), 'Snapchat USB');
    await user.type(
      screen.getByLabelText('Folder path'),
      '/Volumes/SNAP/snapchat-memories',
    );
    await user.click(screen.getByRole('button', { name: 'Add source' }));

    await waitFor(() => {
      expect(
        screen.queryByText('Welcome to Memoria Vault'),
      ).not.toBeInTheDocument();
    });
    expect(startMemorySourceScanMock).toHaveBeenCalledWith(source.id);
    expect(screen.getByText('Scanning memories…')).toBeInTheDocument();
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

describe('App footer', () => {
  it('shows project links and ownership information', async () => {
    getMemorySourcesMock.mockResolvedValue([]);

    render(<App />);

    expect(
      await screen.findByText('All rights reserved Cameron Noupoue.'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'Open source on GitHub, contributions welcome',
      }),
    ).toHaveAttribute('href', 'https://github.com/cnoupoue/memoria-vault');

    expect(screen.getByRole('link', { name: 'LinkedIn' })).toHaveAttribute(
      'href',
      'https://www.linkedin.com/in/cnoupoue',
    );
  });
});

describe('App video preview fallback', () => {
  it('renders a clickable fallback when a video thumbnail is unavailable', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    getMemoriesMock.mockResolvedValue({
      content: [
        {
          id: 'memory-video',
          capturedAt: '2026-01-01',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-video/thumbnail',
          isFavorite: false,
          favoritedAt: null,
        },
      ],
      page: 0,
      size: 48,
      totalElements: 1,
      totalPages: 1,
    });

    render(<App />);

    const thumbnail = await screen.findByAltText('Memory from 2026-01-01');
    fireEvent.error(thumbnail);

    expect(screen.getByText('Video preview unavailable')).toBeInTheDocument();
    expect(screen.getByText('Open video')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Open Memory from 2026-01-01' }),
    );

    await waitFor(() => {
      expect(getMemoryDetailMock).toHaveBeenCalledWith('memory-video');
    });
  });
});

describe('App memory sorting', () => {
  it('loads memories newest first by default and switches immediately both ways', async () => {
    const user = userEvent.setup();
    const newestFirst = [
      buildMemory({ id: 'newest', capturedAt: '2026-03-01' }),
      buildMemory({ id: 'oldest', capturedAt: '2026-01-01' }),
    ];
    const oldestFirst = [...newestFirst].reverse();

    mockSortableArchiveMemories(newestFirst, oldestFirst);

    render(<App />);

    await waitFor(() => {
      expect(memoryCardDates()).toEqual(['2026-03-01', '2026-01-01']);
    });
    expect(getMemoriesMock).toHaveBeenCalledWith(
      2026,
      undefined,
      0,
      48,
      'NEWEST_FIRST',
    );

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Sort memories' }),
      'OLDEST_FIRST',
    );

    await waitFor(() => {
      expect(memoryCardDates()).toEqual(['2026-01-01', '2026-03-01']);
    });
    expect(getMemoriesMock).toHaveBeenLastCalledWith(
      2026,
      undefined,
      0,
      48,
      'OLDEST_FIRST',
    );

    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Sort memories' }),
      'NEWEST_FIRST',
    );

    await waitFor(() => {
      expect(memoryCardDates()).toEqual(['2026-03-01', '2026-01-01']);
    });
    expect(getMemoriesMock).toHaveBeenLastCalledWith(
      2026,
      undefined,
      0,
      48,
      'NEWEST_FIRST',
    );
  });

  it('persists the selected memory sort order between sessions', async () => {
    const user = userEvent.setup();
    const newestFirst = [
      buildMemory({ id: 'newest', capturedAt: '2026-03-01' }),
      buildMemory({ id: 'oldest', capturedAt: '2026-01-01' }),
    ];
    const oldestFirst = [...newestFirst].reverse();

    mockSortableArchiveMemories(newestFirst, oldestFirst);

    const { unmount } = render(<App />);

    await user.selectOptions(
      await screen.findByRole('combobox', { name: 'Sort memories' }),
      'OLDEST_FIRST',
    );

    await waitFor(() => {
      expect(window.localStorage.getItem('memoriaVault.memorySortOrder')).toBe(
        'OLDEST_FIRST',
      );
    });

    unmount();
    getMemoriesMock.mockClear();

    render(<App />);

    expect(
      await screen.findByRole('combobox', { name: 'Sort memories' }),
    ).toHaveValue('OLDEST_FIRST');
    await waitFor(() => {
      expect(getMemoriesMock).toHaveBeenCalledWith(
        2026,
        undefined,
        0,
        48,
        'OLDEST_FIRST',
      );
    });
  });

  it('passes the selected sort order to Favorites', async () => {
    const user = userEvent.setup();
    const favorites = [
      buildMemory({
        id: 'oldest-favorite',
        capturedAt: '2026-01-01',
        isFavorite: true,
        favoritedAt: '2026-07-18T09:00:00Z',
      }),
      buildMemory({
        id: 'newest-favorite',
        capturedAt: '2026-03-01',
        isFavorite: true,
        favoritedAt: '2026-07-18T10:00:00Z',
      }),
    ];

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 2 }]);
    getFavoriteMemoriesMock.mockImplementation(
      (page = 0, size = 48, sortOrder = 'NEWEST_FIRST') =>
        Promise.resolve({
          content:
            sortOrder === 'OLDEST_FIRST' ? favorites : [...favorites].reverse(),
          page,
          size,
          totalElements: favorites.length,
          totalPages: 1,
        }),
    );
    mockMemoryDetails(favorites);

    render(<App />);

    await user.selectOptions(
      await screen.findByRole('combobox', { name: 'Sort memories' }),
      'OLDEST_FIRST',
    );
    await user.click(screen.getByRole('button', { name: 'Favorites' }));

    await waitFor(() => {
      expect(getFavoriteMemoriesMock).toHaveBeenLastCalledWith(
        0,
        48,
        'OLDEST_FIRST',
      );
    });
    expect(memoryCardDates()).toEqual(['2026-01-01', '2026-03-01']);
  });

  it('keeps paginated loading globally ordered by requesting the next page with the selected order', async () => {
    const user = userEvent.setup();
    const pageOne = [
      buildMemory({ id: 'oldest-page-one', capturedAt: '2026-01-01' }),
      buildMemory({
        id: 'newest-page-one',
        capturedAt: '2026-01-02',
        mediaType: 'VIDEO',
      }),
    ];
    const pageTwo = [buildMemory({ id: 'page-two', capturedAt: '2026-01-03' })];

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 3 }]);
    getMemoriesMock.mockImplementation(
      (
        _year?: number,
        _month?: number,
        page = 0,
        size = 48,
        sortOrder: MemorySortOrder = 'NEWEST_FIRST',
      ) =>
        Promise.resolve({
          content:
            page === 0 && sortOrder === 'NEWEST_FIRST'
              ? [...pageOne].reverse()
              : page === 0
                ? pageOne
                : pageTwo,
          page,
          size,
          totalElements: 3,
          totalPages: 2,
        }),
    );
    mockMemoryDetails([...pageOne, ...pageTwo]);

    render(<App />);

    await user.selectOptions(
      await screen.findByRole('combobox', { name: 'Sort memories' }),
      'OLDEST_FIRST',
    );
    await screen.findByRole('button', { name: 'Load more' });
    await user.click(screen.getByRole('button', { name: 'Load more' }));

    await waitFor(() => {
      expect(getMemoriesMock).toHaveBeenLastCalledWith(
        2026,
        undefined,
        1,
        48,
        'OLDEST_FIRST',
      );
    });
    expect(memoryCardDates()).toEqual([
      '2026-01-01',
      '2026-01-02',
      '2026-01-03',
    ]);
  });

  it('keeps viewer navigation in the displayed sorted order', async () => {
    const user = userEvent.setup();
    const newestFirst = [
      buildMemory({ id: 'newest', capturedAt: '2026-03-01' }),
      buildMemory({ id: 'oldest', capturedAt: '2026-01-01' }),
    ];
    const oldestFirst = [...newestFirst].reverse();

    mockSortableArchiveMemories(newestFirst, oldestFirst);

    render(<App />);

    await user.selectOptions(
      await screen.findByRole('combobox', { name: 'Sort memories' }),
      'OLDEST_FIRST',
    );
    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );
    await user.click(viewer().getByRole('button', { name: 'Next memory' }));

    await waitFor(() => {
      expect(viewer().getByText('2026-03-01')).toBeInTheDocument();
    });
  });
});

describe('App favorites', () => {
  it('loads favorite memories from the Favorites page', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    getFavoriteMemoriesMock.mockResolvedValue({
      content: [
        {
          id: 'memory-favorite',
          capturedAt: '2026-02-03',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 2048,
          lastModifiedAt: '2026-02-03T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-favorite/thumbnail',
          isFavorite: true,
          favoritedAt: '2026-07-18T10:15:30Z',
        },
      ],
      page: 0,
      size: 48,
      totalElements: 1,
      totalPages: 1,
    });

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Favorites' }));

    expect(
      await screen.findByRole('heading', { name: 'Favorites' }),
    ).toBeInTheDocument();
    expect(screen.getByAltText('Memory from 2026-02-03')).toBeInTheDocument();
    expect(getFavoriteMemoriesMock).toHaveBeenCalledWith(0, 48, 'NEWEST_FIRST');
  });

  it('shows the empty Favorites state', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 0 }]);

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Favorites' }));

    expect(await screen.findByText('No favorites yet.')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Mark memories with the heart icon to find them here later.',
      ),
    ).toBeInTheDocument();
  });

  it('rolls back optimistic favorite state when the API fails', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    getMemoriesMock.mockResolvedValue({
      content: [
        {
          id: 'memory-video',
          capturedAt: '2026-01-01',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-video/thumbnail',
          isFavorite: false,
          favoritedAt: null,
        },
      ],
      page: 0,
      size: 48,
      totalElements: 1,
      totalPages: 1,
    });
    addMemoryFavoriteMock.mockRejectedValue(new Error('failed'));

    render(<App />);

    const favoriteButton = await screen.findByRole('button', {
      name: 'Add to Favorites',
    });

    await user.click(favoriteButton);

    await waitFor(() => {
      expect(addMemoryFavoriteMock).toHaveBeenCalledWith('memory-video');
    });
    expect(
      await screen.findByText('Could not update Favorites. Try again.'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Add to Favorites' }),
    ).toHaveAttribute('aria-pressed', 'false');
  });

  it('keeps favorite state after refetching the archive', async () => {
    const user = userEvent.setup();

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    getMemoriesMock.mockResolvedValue({
      content: [
        {
          id: 'memory-video',
          capturedAt: '2026-01-01',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1024,
          lastModifiedAt: '2026-01-01T00:00:00Z',
          thumbnailUrl: '/api/memories/memory-video/thumbnail',
          isFavorite: true,
          favoritedAt: '2026-07-18T10:15:30Z',
        },
      ],
      page: 0,
      size: 48,
      totalElements: 1,
      totalPages: 1,
    });

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Archive' }));

    expect(
      await screen.findByRole('button', { name: 'Remove from Favorites' }),
    ).toHaveAttribute('aria-pressed', 'true');
  });
});

describe('App viewer navigation', () => {
  it('opens a memory with the correct current index and boundary controls', async () => {
    const user = userEvent.setup();
    const memories = [
      buildMemory({ id: 'memory-first', capturedAt: '2026-01-01' }),
      buildMemory({ id: 'memory-second', capturedAt: '2026-01-02' }),
    ];

    mockArchiveMemories(memories);

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(
      viewer().getByRole('button', { name: 'Previous memory' }),
    ).toBeDisabled();
    expect(
      viewer().getByRole('button', { name: 'Next memory' }),
    ).not.toBeDisabled();
  });

  it('clicking Next and Previous shows adjacent memories', async () => {
    const user = userEvent.setup();
    const memories = [
      buildMemory({ id: 'memory-first', capturedAt: '2026-01-01' }),
      buildMemory({ id: 'memory-second', capturedAt: '2026-01-02' }),
      buildMemory({ id: 'memory-third', capturedAt: '2026-01-03' }),
    ];

    mockArchiveMemories(memories);

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-02',
      }),
    );
    await waitFor(() => {
      expect(viewer().getByText('2026-01-02')).toBeInTheDocument();
    });

    await user.click(viewer().getByRole('button', { name: 'Next memory' }));

    await waitFor(() => {
      expect(viewer().getByText('2026-01-03')).toBeInTheDocument();
    });
    expect(
      viewer().getByRole('button', { name: 'Next memory' }),
    ).toBeDisabled();

    await user.click(viewer().getByRole('button', { name: 'Previous memory' }));

    await waitFor(() => {
      expect(viewer().getByText('2026-01-02')).toBeInTheDocument();
    });
  });

  it('keyboard arrows navigate only while the viewer is open and Escape closes it', async () => {
    const user = userEvent.setup();
    const memories = [
      buildMemory({ id: 'memory-first', capturedAt: '2026-01-01' }),
      buildMemory({ id: 'memory-second', capturedAt: '2026-01-02' }),
    ];

    mockArchiveMemories(memories);

    render(<App />);

    await user.keyboard('{ArrowRight}');
    expect(getMemoryDetailMock).not.toHaveBeenCalled();

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );

    await user.keyboard('{ArrowRight}');
    await waitFor(() => {
      expect(viewer().getByText('2026-01-02')).toBeInTheDocument();
    });

    await user.keyboard('{ArrowLeft}');
    await waitFor(() => {
      expect(viewer().getByText('2026-01-01')).toBeInTheDocument();
    });

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('supports video memories and does not break one-item lists', async () => {
    const user = userEvent.setup();
    const memories = [
      buildMemory({
        id: 'memory-video-only',
        capturedAt: '2026-01-01',
        mediaType: 'VIDEO',
      }),
    ];

    mockArchiveMemories(memories);

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );

    await waitFor(() => {
      expect(viewer().getByText('2026-01-01')).toBeInTheDocument();
    });
    expect(document.querySelector('video')).toHaveAttribute(
      'src',
      '/api/memories/memory-video-only/media',
    );
    expect(
      viewer().getByRole('button', { name: 'Previous memory' }),
    ).toBeDisabled();
    expect(
      viewer().getByRole('button', { name: 'Next memory' }),
    ).toBeDisabled();
  });

  it('keeps navigation inside Favorites and updates favorite state while moving', async () => {
    const user = userEvent.setup();
    const favorites = [
      buildMemory({
        id: 'favorite-first',
        capturedAt: '2026-02-01',
        isFavorite: true,
        favoritedAt: '2026-07-18T10:00:00Z',
      }),
      buildMemory({
        id: 'favorite-second',
        capturedAt: '2026-02-02',
        isFavorite: false,
        favoritedAt: null,
      }),
    ];

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 3 }]);
    getMemoriesMock.mockResolvedValue({
      content: [
        buildMemory({ id: 'archive-only', capturedAt: '2026-03-01' }),
        ...favorites,
      ],
      page: 0,
      size: 48,
      totalElements: 3,
      totalPages: 1,
    });
    getFavoriteMemoriesMock.mockResolvedValue({
      content: favorites,
      page: 0,
      size: 48,
      totalElements: favorites.length,
      totalPages: 1,
    });
    mockMemoryDetails(favorites);

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Favorites' }));
    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-02-01',
      }),
    );

    expect(
      await viewer().findByRole('button', { name: 'Remove from Favorites' }),
    ).toHaveAttribute('aria-pressed', 'true');

    await user.click(viewer().getByRole('button', { name: 'Next memory' }));

    await waitFor(() => {
      expect(viewer().getByText('2026-02-02')).toBeInTheDocument();
    });
    expect(
      viewer().getByRole('button', { name: 'Add to Favorites' }),
    ).toHaveAttribute('aria-pressed', 'false');
    expect(
      viewer().getByRole('button', { name: 'Next memory' }),
    ).toBeDisabled();
    expect(getMemoryDetailMock).not.toHaveBeenCalledWith('archive-only');
  });

  it('removing the current favorite in Favorites opens the next favorite', async () => {
    const user = userEvent.setup();
    const favorites = [
      buildMemory({
        id: 'favorite-first',
        capturedAt: '2026-02-01',
        isFavorite: true,
        favoritedAt: '2026-07-18T10:00:00Z',
      }),
      buildMemory({
        id: 'favorite-second',
        capturedAt: '2026-02-02',
        isFavorite: true,
        favoritedAt: '2026-07-18T09:00:00Z',
      }),
    ];

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 2 }]);
    getFavoriteMemoriesMock.mockResolvedValue({
      content: favorites,
      page: 0,
      size: 48,
      totalElements: favorites.length,
      totalPages: 1,
    });
    mockMemoryDetails(favorites);
    removeMemoryFavoriteMock.mockResolvedValue({
      ...favorites[0],
      isFavorite: false,
      favoritedAt: null,
    });

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Favorites' }));
    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-02-01',
      }),
    );

    await user.click(
      await viewer().findByRole('button', { name: 'Remove from Favorites' }),
    );

    await waitFor(() => {
      expect(viewer().getByText('2026-02-02')).toBeInTheDocument();
    });
    expect(viewer().queryByText('2026-02-01')).not.toBeInTheDocument();
  });

  it('deleting a memory removes it from the current list and opens the next memory', async () => {
    const user = userEvent.setup();
    const first = buildMemory({ id: 'memory-first', capturedAt: '2026-01-01' });
    const second = buildMemory({
      id: 'memory-second',
      capturedAt: '2026-01-02',
    });
    const memories = [first, second];
    const remainingMemories = [second];

    mockArchiveMemories(memories);
    deleteMemoryMock.mockImplementation(async () => {
      mockArchiveMemories(remainingMemories);
    });

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );
    await user.click(await viewer().findByRole('button', { name: 'Delete' }));
    await user.click(
      screen.getByRole('button', { name: 'Delete permanently' }),
    );

    expect(deleteMemoryMock).toHaveBeenCalledWith('memory-first');
    await waitFor(() => {
      expect(viewer().getByText('2026-01-02')).toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', { name: 'Open Memory from 2026-01-01' }),
    ).not.toBeInTheDocument();
  });

  it('closes the viewer after deleting the only available memory', async () => {
    const user = userEvent.setup();
    const memory = buildMemory({ id: 'memory-only', capturedAt: '2026-01-01' });

    mockArchiveMemories([memory]);
    deleteMemoryMock.mockImplementation(async () => {
      mockArchiveMemories([]);
    });

    render(<App />);

    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2026-01-01',
      }),
    );
    await user.click(await viewer().findByRole('button', { name: 'Delete' }));
    await user.click(
      screen.getByRole('button', { name: 'Delete permanently' }),
    );

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(
      screen.queryByRole('button', { name: 'Open Memory from 2026-01-01' }),
    ).not.toBeInTheDocument();
  });

  it('keeps Flashbacks navigation inside the current flashback results', async () => {
    const user = userEvent.setup();
    const flashbacks: FlashbackResponse = {
      date: '2026-07-18',
      memories: [
        {
          id: 'flashback-first',
          capturedAt: '2020-07-18',
          year: 2020,
          yearsAgo: 6,
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1024,
        },
        {
          id: 'flashback-second',
          capturedAt: '2019-07-18',
          year: 2019,
          yearsAgo: 7,
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 2048,
        },
      ],
    };
    const flashbackDetails = flashbacks.memories.map((memory) =>
      buildMemory({
        id: memory.id,
        capturedAt: memory.capturedAt,
        mediaType: memory.mediaType,
      }),
    );

    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 2 }]);
    getTodayFlashbacksMock.mockResolvedValue(flashbacks);
    mockMemoryDetails(flashbackDetails);

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Flashbacks' }));
    await user.click(
      await screen.findByRole('button', {
        name: 'Open Memory from 2020-07-18',
      }),
    );

    await waitFor(() => {
      expect(viewer().getByText('2020-07-18')).toBeInTheDocument();
    });

    await user.click(viewer().getByRole('button', { name: 'Next memory' }));

    await waitFor(() => {
      expect(viewer().getByText('2019-07-18')).toBeInTheDocument();
    });
    expect(document.querySelector('video')).toHaveAttribute(
      'src',
      '/api/memories/flashback-second/media',
    );
  });

  it('returns to onboarding after resetting application data from settings', async () => {
    const user = userEvent.setup();
    getMemorySourcesMock.mockResolvedValue([buildSource()]);
    getTimelineYearsMock.mockResolvedValue([{ year: 2026, memoryCount: 1 }]);
    resetApplicationDataMock.mockResolvedValue({
      reset: true,
      restartRequired: false,
      removedLocations: [],
      message:
        'Application data was reset. Your original photos and videos were not deleted.',
    });

    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Settings' }));
    await user.click(
      await screen.findByRole('button', { name: 'Reset application data' }),
    );
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Reset application data',
      }),
    );

    expect(
      await screen.findByText('Welcome to Memoria Vault'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Add an exported archive folder to build your private local archive.',
      ),
    ).toBeInTheDocument();
  });
});
