import { afterEach, describe, expect, it, vi } from 'vitest';
import { deleteMemory } from './memoriaVaultApi';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('memoriaVaultApi', () => {
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
