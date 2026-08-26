import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  deleteMemory,
  getFavoriteMemories,
  getMemories,
} from './memoriaVaultApi';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('memoriaVaultApi', () => {
  it('passes memory sort order to archive requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({
        content: [],
        page: 0,
        size: 48,
        totalElements: 0,
        totalPages: 0,
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    await getMemories(2026, 2, 1, 48, 'OLDEST_FIRST');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/memories?page=1&size=48&sortOrder=OLDEST_FIRST&year=2026&month=2',
      undefined,
    );
  });

  it('passes memory sort order to favorites requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({
        content: [],
        page: 0,
        size: 48,
        totalElements: 0,
        totalPages: 0,
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    await getFavoriteMemories(2, 24, 'OLDEST_FIRST');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/memories/favorites?page=2&size=24&sortOrder=OLDEST_FIRST',
      undefined,
    );
  });

  it('sends explicit permanent delete confirmation when deleting a memory', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
    });

    vi.stubGlobal('fetch', fetchMock);

    await deleteMemory('memory-1');

    expect(fetchMock).toHaveBeenCalledWith('/api/memories/memory-1', {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ confirmPermanentDelete: true }),
    });
  });
});
