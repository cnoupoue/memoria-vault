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
  hasPrevious?: boolean;
  hasNext?: boolean;
  onClose: () => void;
  onPrevious?: () => void;
  onNext?: () => void;
  onToggleFavorite?: (memoryId: string, nextFavorite: boolean) => void;
};

export function MemoryViewer({
  memory,
  isLoading,
  error,
  hasPrevious = false,
  hasNext = false,
  onClose,
  onPrevious,
  onNext,
  onToggleFavorite,
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
  const viewerRef = useRef<HTMLElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const lastVideoRef = useRef<HTMLVideoElement | null>(null);

  const hasMediaError = memory !== null && mediaErrorMemoryId === memory.id;
  const mediaErrorMessage = getPlaybackMessage(mediaErrorCategory);
  const playbackSrc =
    playbackState.memoryId === memory?.id && playbackState.src
      ? playbackState.src
      : memory?.mediaUrl;
  const isPreparingPlayback =
    memory?.mediaType === 'VIDEO' &&
    ((playbackState.memoryId !== memory.id &&
      mediaErrorMemoryId !== memory.id) ||
      (playbackState.memoryId === memory.id && playbackState.isPreparing));
  const openOriginalStatus =
    playbackState.memoryId === memory?.id
      ? playbackState.openOriginalStatus
      : null;

  const isOpen = isLoading || error !== null || memory !== null;

  useEffect(() => {
    if (memory?.mediaType !== 'VIDEO') {
      return;
    }

    let isCancelled = false;

    prepareCompatibilityPlayback(memory.id)
      .then((playback) => {
        if (isCancelled) {
          return;
        }

        if (
          (playback.status === 'DIRECT' ||
            playback.status === 'AVAILABLE' ||
            playback.status === 'GENERATED') &&
          playback.mediaUrl
        ) {
          setPlaybackState({
            memoryId: memory.id,
            src: playback.mediaUrl,
            isPreparing: false,
            openOriginalStatus: null,
          });
          setMediaErrorMemoryId(null);
          return;
        }

        if (playback.status === 'FAILED') {
          setMediaErrorMemoryId(memory.id);
          setMediaErrorCategory('VIDEO_FORMAT_UNSUPPORTED');
          setPlaybackState({
            memoryId: memory.id,
            src: null,
            isPreparing: false,
            openOriginalStatus: null,
          });
          return;
        }

        setPlaybackState({
          memoryId: memory.id,
          src: memory.mediaUrl,
          isPreparing: false,
          openOriginalStatus: null,
        });
      })
      .catch(() => {
        if (isCancelled) {
          return;
        }

        setPlaybackState({
          memoryId: memory.id,
          src: memory.mediaUrl,
          isPreparing: false,
          openOriginalStatus: null,
        });
      });

    return () => {
      isCancelled = true;
    };
  }, [memory?.id, memory?.mediaType, memory?.mediaUrl]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (!isOpen) {
        return;
      }

      if (event.key === 'Escape') {
        onClose();
        return;
      }

      if (isSpaceKey(event)) {
        if (
          memory?.mediaType !== 'VIDEO' ||
          hasMediaError ||
          shouldIgnoreVideoPlaybackShortcut(event)
        ) {
          return;
        }

        const video = videoRef.current;

        if (!video) {
          return;
        }

        event.preventDefault();

        if (video.paused) {
          void video.play().catch(() => {
            setMediaErrorMemoryId(memory.id);
            setMediaErrorCategory('BROWSER_MEDIA_ERROR');
          });
        } else {
          video.pause();
        }

        return;
      }

      if (shouldIgnoreMemoryNavigationShortcut(event)) {
        return;
      }

      if (event.key === 'ArrowLeft' && hasPrevious) {
        event.preventDefault();
        onPrevious?.();
        return;
      }

      if (event.key === 'ArrowRight' && hasNext) {
        event.preventDefault();
        onNext?.();
      }
    }

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [
    hasMediaError,
    hasNext,
    hasPrevious,
    isOpen,
    memory,
    onClose,
    onNext,
    onPrevious,
  ]);

  useEffect(() => {
    if (isOpen) {
      viewerRef.current?.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    return () => {
      const video = videoRef.current ?? lastVideoRef.current;

      if (!video) {
        return;
      }

      const hadLoadedSource = video.currentSrc !== '';

      if (!video.paused) {
        video.pause();
      }

      video.removeAttribute('src');

      if (hadLoadedSource) {
        video.load();
      }

      lastVideoRef.current = null;
    };
  }, [memory?.id]);

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
      const playback = await prepareCompatibilityPlayback(memoryId, {
        forceNormalization: true,
      });

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

  function handleVideoElementFailure(video: HTMLVideoElement) {
    if (!memory || memory.mediaType !== 'VIDEO') {
      return;
    }

    const failedSrc = video.currentSrc;

    setMediaErrorMemoryId(memory.id);
    setMediaErrorCategory('BROWSER_MEDIA_ERROR');
    void diagnoseVideoPlaybackFailure(video, memory.mediaUrl).then(
      (diagnostic) => {
        saveLastPlaybackDiagnostic(diagnostic);
        void handleVideoFailure(memory.id, failedSrc, diagnostic.category);
      },
    );
  }

  function handleLoadedVideoMetadata(video: HTMLVideoElement) {
    if (!memory || memory.mediaType !== 'VIDEO') {
      return;
    }

    if (video.videoWidth !== 0 || video.videoHeight !== 0) {
      return;
    }

    handleVideoElementFailure(video);
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
        ref={viewerRef}
        className="memory-viewer"
        onMouseDown={(event) => event.stopPropagation()}
        tabIndex={-1}
      >
        <button
          aria-label="Close viewer"
          className="memory-viewer-close"
          onClick={onClose}
          type="button"
        >
          Close
        </button>

        {!isLoading && !error && memory && onToggleFavorite && (
          <button
            aria-label={
              memory.isFavorite ? 'Remove from Favorites' : 'Add to Favorites'
            }
            aria-pressed={memory.isFavorite}
            className={`memory-viewer-favorite ${
              memory.isFavorite ? 'is-favorite' : ''
            }`}
            onClick={() => onToggleFavorite(memory.id, !memory.isFavorite)}
            title={
              memory.isFavorite ? 'Remove from Favorites' : 'Add to Favorites'
            }
            type="button"
          >
            <span aria-hidden="true">{memory.isFavorite ? '♥' : '♡'}</span>
          </button>
        )}

        {isLoading && (
          <div className="memory-viewer-state">Opening memory…</div>
        )}

        {!isLoading && error && (
          <div className="memory-viewer-state memory-viewer-error">{error}</div>
        )}

        {!isLoading && !error && memory && (
          <>
            <button
              aria-label="Previous memory"
              className="memory-viewer-nav memory-viewer-nav-previous"
              disabled={!hasPrevious}
              onClick={onPrevious}
              type="button"
            >
              <span aria-hidden="true">‹</span>
              <span>Previous</span>
            </button>

            <button
              aria-label="Next memory"
              className="memory-viewer-nav memory-viewer-nav-next"
              disabled={!hasNext}
              onClick={onNext}
              type="button"
            >
              <span>Next</span>
              <span aria-hidden="true">›</span>
            </button>

            <div className="memory-viewer-media">
              {isPreparingPlayback ? (
                <div className="memory-viewer-state">
                  Preparing this video for playback…
                </div>
              ) : hasMediaError ? (
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
                        Open in default video player
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
                  key={memory.id}
                  ref={(element) => {
                    videoRef.current = element;
                    if (element) {
                      lastVideoRef.current = element;
                    }
                  }}
                  autoPlay
                  className="memory-viewer-video"
                  controls
                  onError={(event) =>
                    handleVideoElementFailure(event.currentTarget)
                  }
                  onLoadedMetadata={(event) =>
                    handleLoadedVideoMetadata(event.currentTarget)
                  }
                  onStalled={(event) =>
                    handleVideoElementFailure(event.currentTarget)
                  }
                  playsInline
                  src={
                    playbackSrc ??
                    memory.mediaUrl ??
                    `/api/memories/${memory.id}/media`
                  }
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
              {memory.mediaType === 'VIDEO' && (
                <div className="memory-viewer-footer-action">
                  <button
                    className="secondary-button memory-viewer-native-open"
                    onClick={() => void handleOpenOriginal(memory.id)}
                    type="button"
                  >
                    Open in default video player
                  </button>
                  {openOriginalStatus && <span>{openOriginalStatus}</span>}
                </div>
              )}
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

function isSpaceKey(event: KeyboardEvent) {
  return (
    event.key === ' ' || event.key === 'Spacebar' || event.code === 'Space'
  );
}

function hasShortcutModifier(event: KeyboardEvent) {
  return event.metaKey || event.ctrlKey || event.altKey || event.shiftKey;
}

function shouldIgnoreMemoryNavigationShortcut(event: KeyboardEvent) {
  if (
    hasShortcutModifier(event) ||
    (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight')
  ) {
    return true;
  }

  return isEditableShortcutTarget(event.target, false);
}

function shouldIgnoreVideoPlaybackShortcut(event: KeyboardEvent) {
  if (hasShortcutModifier(event)) {
    return true;
  }

  return isEditableShortcutTarget(event.target, true);
}

function isEditableShortcutTarget(
  eventTarget: EventTarget | null,
  includeButtons: boolean,
) {
  const target = eventTarget instanceof HTMLElement ? eventTarget : null;

  return (
    target?.tagName === 'INPUT' ||
    target?.tagName === 'TEXTAREA' ||
    target?.tagName === 'SELECT' ||
    (includeButtons && target?.tagName === 'BUTTON') ||
    target?.isContentEditable === true ||
    target?.getAttribute('contenteditable') === 'true'
  );
}

function canOpenOriginal(category: PlaybackFailureCategory) {
  return (
    category === 'VIDEO_FORMAT_UNSUPPORTED' ||
    category === 'BROWSER_MEDIA_ERROR'
  );
}
