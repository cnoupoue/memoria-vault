import { useEffect, useState } from 'react';
import type { MemoryDetail } from '../api/types';

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

  const hasMediaError = memory !== null && mediaErrorMemoryId === memory.id;

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

  if (!isOpen) {
    return null;
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
          aria-label="Close viewer"
          className="memory-viewer-close"
          onClick={onClose}
          type="button"
        >
          ×
        </button>

        {isLoading && (
          <div className="memory-viewer-state">Loading Memory…</div>
        )}

        {!isLoading && error && (
          <div className="memory-viewer-state memory-viewer-error">{error}</div>
        )}

        {!isLoading && !error && memory && (
          <>
            <div className="memory-viewer-media">
              {hasMediaError ? (
                <div className="memory-viewer-state memory-viewer-error">
                  This Memory could not be loaded. Check that the USB drive is
                  connected and the source folder is available.
                </div>
              ) : memory.mediaType === 'IMAGE' ? (
                <img
                  alt={`Snapchat Memory from ${memory.capturedAt}`}
                  className="memory-viewer-image"
                  onError={() => {
                    setMediaErrorMemoryId(memory.id);
                  }}
                  src={memory.mediaUrl}
                />
              ) : (
                <video
                  autoPlay
                  className="memory-viewer-video"
                  controls
                  onError={() => {
                    setMediaErrorMemoryId(memory.id);
                  }}
                  playsInline
                  src={memory.mediaUrl}
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
