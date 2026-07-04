import { useEffect, useRef, useState } from 'react';
import {
  openOriginalFile,
  prepareCompatibilityPlayback,
} from '../api/memoriaVaultApi';
import type { MemoryDetail } from '../api/types';
import {
  diagnoseVideoPlaybackFailure,
  getPlaybackMessage,
  savePlaybackFallbackResult,
  saveLastPlaybackDiagnostic,
  type PlaybackFailureCategory,
} from '../videoPlaybackDiagnostics';

type MemoryViewerProps = {
  memory: MemoryDetail | null;
  isLoading: boolean;
  error: string | null;
  onClose: () => void;
};

export function MemoryViewer({
  memory,
  isLoading,
  error,
  onClose,
}: MemoryViewerProps) {
  const [mediaErrorMemoryId, setMediaErrorMemoryId] = useState<string | null>(
    null,
  );
  const [mediaErrorCategory, setMediaErrorCategory] =
    useState<PlaybackFailureCategory>('BROWSER_MEDIA_ERROR');
  const [playbackState, setPlaybackState] = useState<{
    memoryId: string | null;
    src: string | null;
    isPreparing: boolean;
    openOriginalStatus: string | null;
  }>({
    memoryId: null,
    src: null,
    isPreparing: false,
    openOriginalStatus: null,
  });
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);

  const hasMediaError = memory !== null && mediaErrorMemoryId === memory.id;
  const mediaErrorMessage = getPlaybackMessage(mediaErrorCategory);
  const playbackSrc =
    playbackState.memoryId === memory?.id
      ? playbackState.src
      : memory?.mediaUrl;
  const isPreparingPlayback =
    playbackState.memoryId === memory?.id && playbackState.isPreparing;
  const openOriginalStatus =
    playbackState.memoryId === memory?.id
      ? playbackState.openOriginalStatus
      : null;

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const isOpen = isLoading || error !== null || memory !== null;

  useEffect(() => {
    if (isOpen) {
      closeButtonRef.current?.focus();
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  async function handleVideoFailure(
    memoryId: string,
    failedSrc: string,
    category: PlaybackFailureCategory,
  ) {
    if (
      memory?.mediaUrl &&
      failedSrc !== '' &&
      !failedSrc.endsWith(memory.mediaUrl)
    ) {
      setMediaErrorCategory(category);
      return;
    }

    if (!shouldAttemptCompatibilityPlayback(category)) {
      setMediaErrorCategory(category);
      return;
    }

    setPlaybackState({
      memoryId,
      src: memory?.mediaUrl ?? null,
      isPreparing: true,
      openOriginalStatus: null,
    });

    try {
      const playback = await prepareCompatibilityPlayback(memoryId);

      if (
        (playback.status === 'AVAILABLE' || playback.status === 'GENERATED') &&
        playback.mediaUrl
      ) {
        savePlaybackFallbackResult(
          playback.status === 'GENERATED' ? 'Generated' : 'Available',
        );
        setPlaybackState({
          memoryId,
          src: playback.mediaUrl,
          isPreparing: false,
          openOriginalStatus: null,
        });
        setMediaErrorMemoryId(null);
        return;
      }

      savePlaybackFallbackResult(
        playback.status === 'FAILED' ? 'Failed' : 'Unavailable',
      );
      setMediaErrorCategory('VIDEO_FORMAT_UNSUPPORTED');
    } catch {
      savePlaybackFallbackResult('Failed');
      setMediaErrorCategory('VIDEO_FORMAT_UNSUPPORTED');
    } finally {
      setPlaybackState((current) =>
        current.memoryId === memoryId
          ? {
              ...current,
              isPreparing: false,
            }
          : current,
      );
    }
  }

  async function handleOpenOriginal(memoryId: string) {
    setPlaybackState((current) => ({
      ...current,
      memoryId,
      openOriginalStatus: null,
    }));

    try {
      await openOriginalFile(memoryId);
      setPlaybackState((current) => ({
        ...current,
        memoryId,
        openOriginalStatus: 'Opened in your default media player.',
      }));
    } catch {
      setPlaybackState((current) => ({
        ...current,
        memoryId,
        openOriginalStatus: 'The original file could not be opened locally.',
      }));
    }
  }

  return (
    <div
      aria-modal="true"
      className="memory-viewer-backdrop"
      onMouseDown={onClose}
      role="dialog"
    >
      <section
        className="memory-viewer"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button
          ref={closeButtonRef}
          aria-label="Close viewer"
          className="memory-viewer-close"
          onClick={onClose}
          type="button"
        >
          Close
        </button>

        {isLoading && (
          <div className="memory-viewer-state">Opening memory…</div>
        )}

        {!isLoading && error && (
          <div className="memory-viewer-state memory-viewer-error">{error}</div>
        )}

        {!isLoading && !error && memory && (
          <>
            <div className="memory-viewer-media">
              {isPreparingPlayback && (
                <div className="memory-viewer-state">
                  Preparing this video for playback…
                </div>
              )}

              {hasMediaError ? (
                <div className="memory-viewer-state memory-viewer-error">
                  <strong>{mediaErrorMessage.title}</strong>
                  <span>{mediaErrorMessage.detail}</span>
                  {memory.mediaType === 'VIDEO' &&
                    canOpenOriginal(mediaErrorCategory) && (
                      <button
                        className="secondary-button"
                        onClick={() => void handleOpenOriginal(memory.id)}
                        type="button"
                      >
                        Open original file
                      </button>
                    )}
                  {openOriginalStatus && <span>{openOriginalStatus}</span>}
                </div>
              ) : memory.mediaType === 'IMAGE' ? (
                <img
                  alt={`Memory from ${memory.capturedAt}`}
                  className="memory-viewer-image"
                  onError={() => {
                    setMediaErrorMemoryId(memory.id);
                    setMediaErrorCategory('MEDIA_FILE_MISSING');
                  }}
                  src={memory.mediaUrl}
                />
              ) : (
                <video
                  autoPlay
                  className="memory-viewer-video"
                  controls
                  onError={(event) => {
                    const failedSrc = event.currentTarget.currentSrc;

                    setMediaErrorMemoryId(memory.id);
                    setMediaErrorCategory('BROWSER_MEDIA_ERROR');
                    void diagnoseVideoPlaybackFailure(
                      event.currentTarget,
                      memory.mediaUrl,
                    ).then((diagnostic) => {
                      saveLastPlaybackDiagnostic(diagnostic);
                      void handleVideoFailure(
                        memory.id,
                        failedSrc,
                        diagnostic.category,
                      );
                    });
                  }}
                  playsInline
                  src={playbackSrc ?? memory.mediaUrl}
                />
              )}

              {!hasMediaError && memory.overlayUrl && (
                <img
                  alt=""
                  aria-hidden="true"
                  className="memory-viewer-overlay"
                  src={memory.overlayUrl}
                />
              )}
            </div>

            <footer className="memory-viewer-footer">
              <div>
                <strong>{memory.capturedAt}</strong>
                <span>
                  {memory.mediaType === 'VIDEO' ? 'Video' : 'Photo'}
                  {' · '}
                  {(memory.fileSizeBytes / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
            </footer>
          </>
        )}
      </section>
    </div>
  );
}

function shouldAttemptCompatibilityPlayback(category: PlaybackFailureCategory) {
  return (
    category === 'VIDEO_FORMAT_UNSUPPORTED' ||
    category === 'BROWSER_MEDIA_ERROR'
  );
}

function canOpenOriginal(category: PlaybackFailureCategory) {
  return (
    category === 'VIDEO_FORMAT_UNSUPPORTED' ||
    category === 'BROWSER_MEDIA_ERROR'
  );
}
