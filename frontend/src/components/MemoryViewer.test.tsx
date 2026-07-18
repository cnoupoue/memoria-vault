import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
  it('renders previous and next controls with disabled boundary state', () => {
    render(
      <MemoryViewer
        error={null}
        hasNext
        hasPrevious={false}
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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('button', { name: 'Previous memory' }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', { name: 'Next memory' }),
    ).not.toBeDisabled();
  });

  it('uses click and keyboard navigation callbacks while open', async () => {
    const user = userEvent.setup();
    const onPrevious = vi.fn();
    const onNext = vi.fn();
    const onClose = vi.fn();

    render(
      <MemoryViewer
        error={null}
        hasNext
        hasPrevious
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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={onClose}
        onNext={onNext}
        onPrevious={onPrevious}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Next memory' }));
    await user.click(screen.getByRole('button', { name: 'Previous memory' }));
    fireEvent.keyDown(window, { key: 'ArrowRight' });
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    fireEvent.keyDown(window, { key: 'Escape' });

    expect(onNext).toHaveBeenCalledTimes(2);
    expect(onPrevious).toHaveBeenCalledTimes(2);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not use arrow callbacks when the relevant direction is disabled', () => {
    const onPrevious = vi.fn();
    const onNext = vi.fn();

    render(
      <MemoryViewer
        error={null}
        hasNext={false}
        hasPrevious={false}
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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
        onNext={onNext}
        onPrevious={onPrevious}
      />,
    );

    fireEvent.keyDown(window, { key: 'ArrowRight' });
    fireEvent.keyDown(window, { key: 'ArrowLeft' });

    expect(onNext).not.toHaveBeenCalled();
    expect(onPrevious).not.toHaveBeenCalled();
  });

  it('does not use arrow navigation while focus is inside editable controls', () => {
    const onPrevious = vi.fn();
    const onNext = vi.fn();

    render(
      <>
        <input aria-label="Search" />
        <textarea aria-label="Notes" />
        <select aria-label="Filter">
          <option>All</option>
        </select>
        <div aria-label="Editable" contentEditable role="textbox" />
        <MemoryViewer
          error={null}
          hasNext
          hasPrevious
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
            isFavorite: false,
            favoritedAt: null,
          }}
          onClose={vi.fn()}
          onNext={onNext}
          onPrevious={onPrevious}
        />
      </>,
    );

    fireEvent.keyDown(screen.getByLabelText('Search'), { key: 'ArrowRight' });
    fireEvent.keyDown(screen.getByLabelText('Notes'), { key: 'ArrowLeft' });
    fireEvent.keyDown(screen.getByLabelText('Filter'), { key: 'ArrowRight' });
    fireEvent.keyDown(screen.getByLabelText('Editable'), { key: 'ArrowLeft' });

    expect(onNext).not.toHaveBeenCalled();
    expect(onPrevious).not.toHaveBeenCalled();
  });

  it('ignores arrow navigation events with modifier keys', () => {
    const onPrevious = vi.fn();
    const onNext = vi.fn();

    render(
      <MemoryViewer
        error={null}
        hasNext
        hasPrevious
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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
        onNext={onNext}
        onPrevious={onPrevious}
      />,
    );

    fireEvent.keyDown(window, { key: 'ArrowRight', metaKey: true });
    fireEvent.keyDown(window, { key: 'ArrowRight', ctrlKey: true });
    fireEvent.keyDown(window, { key: 'ArrowLeft', altKey: true });
    fireEvent.keyDown(window, { key: 'ArrowLeft', shiftKey: true });

    expect(onNext).not.toHaveBeenCalled();
    expect(onPrevious).not.toHaveBeenCalled();
  });

  it('stops the previous video when navigating away from it', () => {
    const pauseSpy = vi
      .spyOn(HTMLMediaElement.prototype, 'pause')
      .mockImplementation(() => {});
    const loadSpy = vi
      .spyOn(HTMLMediaElement.prototype, 'load')
      .mockImplementation(() => {});
    const { rerender } = render(
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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
      />,
    );

    const video = document.querySelector('video') as HTMLVideoElement;
    Object.defineProperty(video, 'paused', {
      configurable: true,
      value: false,
    });
    Object.defineProperty(video, 'currentSrc', {
      configurable: true,
      value: 'http://127.0.0.1:8080/api/memories/memory-video/media',
    });

    rerender(
      <MemoryViewer
        error={null}
        isLoading={false}
        memory={{
          id: 'memory-image',
          capturedAt: '2020-06-11',
          mediaType: 'IMAGE',
          hasOverlay: false,
          fileSizeBytes: 1_500_000,
          lastModifiedAt: '2020-06-11T10:00:00Z',
          mediaUrl: '/api/memories/memory-image/media',
          overlayUrl: null,
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
      />,
    );

    expect(pauseSpy).toHaveBeenCalled();
    expect(loadSpy).toHaveBeenCalled();
  });

  it('toggles favorite state from the detail view', async () => {
    const onToggleFavorite = vi.fn();

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
          isFavorite: false,
          favoritedAt: null,
        }}
        onClose={vi.fn()}
        onToggleFavorite={onToggleFavorite}
      />,
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Add to Favorites' }),
    );

    expect(onToggleFavorite).toHaveBeenCalledWith('memory-1', true);
  });

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
          isFavorite: false,
          favoritedAt: null,
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
          isFavorite: false,
          favoritedAt: null,
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
          isFavorite: false,
          favoritedAt: null,
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
          isFavorite: false,
          favoritedAt: null,
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
