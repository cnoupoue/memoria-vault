import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  diagnoseVideoPlaybackFailure,
  getPlaybackMessage,
  saveLastPlaybackDiagnostic,
} from './videoPlaybackDiagnostics';

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

describe('videoPlaybackDiagnostics', () => {
  it('maps a successful stream probe with media error code 4 to unsupported format', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('', {
          status: 206,
          headers: {
            'Accept-Ranges': 'bytes',
            'Content-Type': 'video/mp4',
          },
        }),
      ),
    );
    const video = videoElementWithError(4);

    const diagnostic = await diagnoseVideoPlaybackFailure(
      video,
      '/api/memories/private-id/media',
    );

    expect(diagnostic.category).toBe('VIDEO_FORMAT_UNSUPPORTED');
    expect(diagnostic.httpStatus).toBe(206);
    expect(diagnostic.rangeRequestsSupported).toBe(true);
    expect(diagnostic.mimeType).toBe('video/mp4');
  });

  it('keeps backend source unavailable distinct from browser errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SOURCE_UNAVAILABLE' }), {
          status: 404,
          headers: {
            'Content-Type': 'application/json',
          },
        }),
      ),
    );

    const diagnostic = await diagnoseVideoPlaybackFailure(
      videoElementWithError(2),
      '/api/memories/private-id/media',
    );

    expect(diagnostic.category).toBe('SOURCE_UNAVAILABLE');
    expect(getPlaybackMessage(diagnostic.category).detail).toContain(
      'Reconnect the drive',
    );
  });

  it('redacts URLs and paths from stored diagnostics', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
    const video = videoElementWithError(
      4,
      'Cannot play http://localhost:8080/api/memories/private-id/media from /Users/cameron/private.mov',
    );

    const diagnostic = await diagnoseVideoPlaybackFailure(
      video,
      '/api/memories/private-id/media',
    );

    saveLastPlaybackDiagnostic(diagnostic);

    const storedDiagnostic = window.localStorage.getItem(
      'memoriaVault.lastVideoPlaybackDiagnostic',
    );

    expect(storedDiagnostic).not.toContain('private-id');
    expect(storedDiagnostic).not.toContain('/Users/cameron');
    expect(storedDiagnostic).not.toContain('private.mov');
    expect(storedDiagnostic).toContain('[redacted-url]');
  });
});

function videoElementWithError(code: number, message = 'Media error') {
  const video = document.createElement('video');

  Object.defineProperty(video, 'error', {
    configurable: true,
    value: {
      code,
      message,
    },
  });
  Object.defineProperty(video, 'currentSrc', {
    configurable: true,
    value: 'http://localhost/api/memories/private-id/media',
  });

  return video;
}
