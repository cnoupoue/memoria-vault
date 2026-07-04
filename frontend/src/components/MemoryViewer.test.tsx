import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { prepareCompatibilityPlayback } from '../api/memoriaVaultApi';
import { MemoryViewer } from './MemoryViewer';

vi.mock('../api/memoriaVaultApi', () => ({
  openOriginalFile: vi.fn(),
  prepareCompatibilityPlayback: vi.fn(),
}));

const prepareCompatibilityPlaybackMock = vi.mocked(
  prepareCompatibilityPlayback,
);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  window.localStorage.clear();
});

describe('MemoryViewer', () => {
  it('renders an image memory', () => {
    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-1',
          capturedAt: '2020-06-10',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-10T10:00:00Z',
          mediaUrl: '/api/memories/memory-1/media',
          overlayUrl: null,
        }}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    expect(
      screen.getByRole('img', {
        name: 'Memory from 2020-06-10',
      }),
    ).toHaveAttribute('src', '/api/memories/memory-1/media');
  });

  it('does not render when no memory is selected', () => {
    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={null}
        onClose={vi.fn()}
      />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows a missing original file message when image media cannot be loaded', () => {
    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-1',
          capturedAt: '2020-06-10',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-10T10:00:00Z',
          mediaUrl: '/api/memories/memory-1/media',
          overlayUrl: null,
        }}
        onClose={vi.fn()}
      />,
    );

    fireEvent.error(screen.getByRole('img'));

    expect(
      screen.getByText('This original file could not be found.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Try rescanning the source if files were moved or changed.',
      ),
    ).toBeInTheDocument();
  });

  it('does not describe browser decoder failures as disconnected drives', async () => {
    prepareCompatibilityPlaybackMock.mockResolvedValue({
      status: 'UNAVAILABLE',
      mediaUrl: null,
      message: 'Compatibility playback is unavailable.',
    });
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

    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-video',
          capturedAt: '2020-06-10',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-10T10:00:00Z',
          mediaUrl: '/api/memories/memory-video/media',
          overlayUrl: null,
        }}
        onClose={vi.fn()}
      />,
    );

    const video = document.querySelector('video') as HTMLVideoElement;
    Object.defineProperty(video, 'error', {
      configurable: true,
      value: {
        code: 4,
        message: 'Format unsupported',
      },
    });

    fireEvent.error(video);

    expect(
      await screen.findByText(
        'This video is available, but this browser cannot play its format.',
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/Connect the drive containing this source/i),
    ).not.toBeInTheDocument();

    await waitFor(() => {
      expect(window.localStorage.length).toBe(1);
    });
  });

  it('automatically uses generated compatibility playback after decoder failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('', {
          status: 206,
          headers: {
            'Accept-Ranges': 'bytes',
            'Content-Type': 'video/quicktime',
          },
        }),
      ),
    );
    prepareCompatibilityPlaybackMock.mockResolvedValue({
      status: 'GENERATED',
      mediaUrl: '/api/memories/memory-video/playback/compatible/media',
      message: 'Compatibility playback is ready.',
    });

    render(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-video',
          capturedAt: '2020-06-10',
          mediaType: 'VIDEO',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-10T10:00:00Z',
          mediaUrl: '/api/memories/memory-video/media',
          overlayUrl: null,
        }}
        onClose={vi.fn()}
      />,
    );

    const video = document.querySelector('video') as HTMLVideoElement;
    Object.defineProperty(video, 'error', {
      configurable: true,
      value: {
        code: 4,
        message: 'Format unsupported',
      },
    });
    Object.defineProperty(video, 'currentSrc', {
      configurable: true,
      value: 'http://127.0.0.1:8080/api/memories/memory-video/media',
    });

    fireEvent.error(video);

    await waitFor(() => {
      expect(document.querySelector('video')).toHaveAttribute(
        'src',
        '/api/memories/memory-video/playback/compatible/media',
      );
    });
    expect(prepareCompatibilityPlaybackMock).toHaveBeenCalledWith(
      'memory-video',
    );
  });
});
