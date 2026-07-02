import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  createMemorySource,
  deleteMemorySource,
  getLatestMemorySourceScan,
  getMemoryScanJob,
  getMemorySourceAvailability,
  getMemorySources,
  selectMemorySourceFolder,
  SnapmemoriaApiError,
  startMemorySourceScan,
} from '../api/snapmemoriaApi';
import type {
  MemorySource,
  MemoryScanJob,
  SourceAvailabilityStatus,
} from '../api/types';

function formatDate(value: string | null): string {
  if (!value) {
    return 'Never';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function getStatusLabel(status: string | null): string {
  if (!status) {
    return 'Not scanned';
  }

  return status
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

function getAvailabilityLabel(status: SourceAvailabilityStatus): string {
  switch (status) {
    case 'AVAILABLE':
      return 'Available';
    case 'UNAVAILABLE':
      return 'Folder moved or missing';
    case 'NOT_A_DIRECTORY':
      return 'USB drive unavailable';
    case 'NOT_READABLE':
      return 'Folder is not readable';
  }
}

function getSourceStateLabel(
  source: MemorySource,
  isScanning: boolean,
): string {
  if (isScanning) {
    return 'Scan in progress';
  }

  if (source.lastScanStatus === 'FAILED') {
    return 'Last scan failed';
  }

  return getAvailabilityLabel(source.availabilityStatus);
}

type SettingsPageProps = {
  autoOpenFolderPicker?: boolean;
  autoFocusSourceForm?: boolean;
  onFolderPickerAutoOpened?: () => void;
  onSourceCreated?: (source: MemorySource) => void;
  onSourceDeleted?: (sourceId: string) => void;
  onSourceScanned: () => void;
};

export function SettingsPage({
  autoOpenFolderPicker = false,
  autoFocusSourceForm = false,
  onFolderPickerAutoOpened,
  onSourceCreated,
  onSourceDeleted,
  onSourceScanned,
}: SettingsPageProps) {
  const [sources, setSources] = useState<MemorySource[]>([]);
  const [name, setName] = useState('');
  const [rootPath, setRootPath] = useState('');

  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [isSelectingFolder, setIsSelectingFolder] = useState(false);
  const [scanningSourceId, setScanningSourceId] = useState<string | null>(null);
  const [deletingSourceId, setDeletingSourceId] = useState<string | null>(null);
  const [refreshingAvailabilitySourceId, setRefreshingAvailabilitySourceId] =
    useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [folderPickerMessage, setFolderPickerMessage] = useState<string | null>(
    null,
  );
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [scanJob, setScanJob] = useState<MemoryScanJob | null>(null);

  const pollingIntervalRef = useRef<number | null>(null);
  const nameInputRef = useRef<HTMLInputElement | null>(null);

  function upsertSource(source: MemorySource) {
    setSources((currentSources) => {
      const sourceExists = currentSources.some((item) => item.id === source.id);

      if (!sourceExists) {
        return [...currentSources, source];
      }

      return currentSources.map((item) =>
        item.id === source.id ? source : item,
      );
    });
  }

  const handleChooseFolder = useCallback(async () => {
    setIsSelectingFolder(true);
    setError(null);
    setFolderPickerMessage(null);
    setSuccessMessage(null);

    try {
      const selection = await selectMemorySourceFolder();

      if (!selection.selected || selection.path === null) {
        return;
      }

      setRootPath(selection.path);
      setName((currentName) => currentName || selection.name || '');
    } catch (selectionError) {
      setFolderPickerMessage(
        selectionError instanceof SnapmemoriaApiError
          ? selectionError.message
          : 'Folder selection is unavailable. Enter the folder path manually.',
      );
    } finally {
      setIsSelectingFolder(false);
    }
  }, []);

  const loadSources = useCallback(async () => {
    try {
      const data = await getMemorySources();
      setSources(data);
    } catch {
      setError(
        'Could not load memory sources. Check that the backend is running.',
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  const stopPolling = useCallback(() => {
    if (pollingIntervalRef.current !== null) {
      window.clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  }, []);

  const handleFinishedScan = useCallback(
    async (job: MemoryScanJob) => {
      stopPolling();
      setScanningSourceId(null);

      await loadSources();

      if (job.status === 'COMPLETED') {
        onSourceScanned();
        return;
      }

      setError(job.errorMessage ?? 'The scan failed unexpectedly.');
    },
    [loadSources, onSourceScanned, stopPolling],
  );

  const startPolling = useCallback(
    (scanJobId: string, sourceId: string) => {
      stopPolling();

      pollingIntervalRef.current = window.setInterval(() => {
        void (async () => {
          try {
            const updatedJob = await getMemoryScanJob(scanJobId);

            setScanJob(updatedJob);

            if (
              updatedJob.status === 'COMPLETED' ||
              updatedJob.status === 'FAILED'
            ) {
              await handleFinishedScan(updatedJob);
            }
          } catch {
            stopPolling();
            setScanningSourceId(null);
            setError('Could not retrieve scan progress.');
          }
        })();
      }, 1000);

      setScanningSourceId(sourceId);
    },
    [handleFinishedScan, stopPolling],
  );

  useEffect(() => {
    return () => {
      stopPolling();
    };
  }, [stopPolling]);

  useEffect(() => {
    if (autoFocusSourceForm) {
      nameInputRef.current?.focus();
    }
  }, [autoFocusSourceForm]);

  useEffect(() => {
    if (!autoOpenFolderPicker) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      onFolderPickerAutoOpened?.();
      void handleChooseFolder();
    }, 0);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [autoOpenFolderPicker, handleChooseFolder, onFolderPickerAutoOpened]);

  useEffect(() => {
    let isMounted = true;

    void (async () => {
      try {
        const data = await getMemorySources();

        if (isMounted) {
          setSources(data);
        }
      } catch {
        if (isMounted) {
          setError(
            'Could not load memory sources. Check that the backend is running.',
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  function handleRefreshSources() {
    setIsLoading(true);
    setError(null);
    setSuccessMessage(null);
    void loadSources();
  }

  async function handleRefreshSourceAvailability(source: MemorySource) {
    setRefreshingAvailabilitySourceId(source.id);
    setError(null);

    try {
      const availability = await getMemorySourceAvailability(source.id);

      setSources((currentSources) =>
        currentSources.map((item) =>
          item.id === source.id ? { ...item, ...availability } : item,
        ),
      );
    } catch {
      setError('Could not refresh this source status.');
    } finally {
      setRefreshingAvailabilitySourceId(null);
    }
  }

  useEffect(() => {
    async function restoreRunningScan() {
      try {
        const configuredSources = await getMemorySources();

        for (const source of configuredSources) {
          try {
            const latestScanJob = await getLatestMemorySourceScan(source.id);

            if (latestScanJob.status === 'RUNNING') {
              setScanJob(latestScanJob);
              startPolling(latestScanJob.id, source.id);
              return;
            }
          } catch {
            // A source without previous scans returns 404.
          }
        }
      } catch {
        // loadSources() handles visible errors separately.
      }
    }

    void restoreRunningScan();
  }, [startPolling]);

  async function handleCreateSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!name.trim() || !rootPath.trim()) {
      setError('A source name and folder path are required.');
      return;
    }

    setIsCreating(true);
    setError(null);
    setFolderPickerMessage(null);
    setSuccessMessage(null);
    setScanJob(null);

    try {
      const createdSource = await createMemorySource({
        name: name.trim(),
        rootPath: rootPath.trim(),
      });

      upsertSource(createdSource);
      setSuccessMessage('Your source was added. Scanning Memories locally…');
      onSourceCreated?.(createdSource);

      setName('');
      setRootPath('');

      try {
        const startedJob = await startMemorySourceScan(createdSource.id);

        setScanJob(startedJob);
        startPolling(startedJob.id, createdSource.id);
      } catch (scanError) {
        setError(
          scanError instanceof SnapmemoriaApiError
            ? scanError.message
            : 'The source was added, but the scan could not start automatically.',
        );
      }
    } catch {
      setError(
        'Could not add this source. It may already exist or the path may be invalid.',
      );
    } finally {
      setIsCreating(false);
    }
  }

  function getProgressPercent(job: MemoryScanJob): number {
    if (job.totalFiles === 0) {
      return 0;
    }

    return Math.min(
      100,
      Math.round((job.filesProcessed / job.totalFiles) * 100),
    );
  }

  async function handleScan(source: MemorySource) {
    setError(null);
    setSuccessMessage(null);
    setScanJob(null);

    try {
      const startedJob = await startMemorySourceScan(source.id);

      setScanJob(startedJob);
      startPolling(startedJob.id, source.id);
    } catch (scanError) {
      setError(
        scanError instanceof SnapmemoriaApiError
          ? scanError.message
          : 'Could not start this scan. A scan may already be running for this source.',
      );
    }
  }

  async function handleDelete(source: MemorySource) {
    const confirmed = window.confirm(
      `Remove "${source.name}" from SnapMemoria?\n\nThis only removes the configured source. It will not delete any files from your drive.`,
    );

    if (!confirmed) {
      return;
    }
    if (scanningSourceId === source.id) {
      setError('A running source cannot be removed.');
      return;
    }

    setDeletingSourceId(source.id);
    setError(null);
    setSuccessMessage(null);
    setScanJob(null);

    try {
      await deleteMemorySource(source.id);

      setSources((currentSources) =>
        currentSources.filter((item) => item.id !== source.id),
      );
      onSourceDeleted?.(source.id);
      onSourceScanned();
    } catch {
      setError('Could not remove this source.');
    } finally {
      setDeletingSourceId(null);
    }
  }

  return (
    <section className="content">
      <header className="content-header">
        <div>
          <p className="eyebrow">Local configuration</p>
          <h2>Settings</h2>
        </div>
      </header>

      {error && <div className="error-banner">{error}</div>}
      {successMessage && (
        <div className="scan-result-banner">
          <strong>{successMessage}</strong>
          <span>SnapMemoria indexes your Memories in place.</span>
        </div>
      )}

      {scanJob && (
        <div className="scan-result-banner">
          <strong>
            {scanJob.status === 'RUNNING'
              ? 'Scanning Memories…'
              : scanJob.status === 'COMPLETED'
                ? 'Scan completed'
                : 'Scan failed'}
          </strong>

          {scanJob.status === 'RUNNING' ? (
            <>
              <span>
                {scanJob.totalFiles === 0
                  ? 'Counting files…'
                  : `${scanJob.filesProcessed.toLocaleString()} / ${scanJob.totalFiles.toLocaleString()} files processed`}
              </span>

              {scanJob.totalFiles > 0 && (
                <div
                  aria-label={`${getProgressPercent(scanJob)}% complete`}
                  className="scan-progress-track"
                  role="progressbar"
                  aria-valuemax={100}
                  aria-valuemin={0}
                  aria-valuenow={getProgressPercent(scanJob)}
                >
                  <div
                    className="scan-progress-value"
                    style={{ width: `${getProgressPercent(scanJob)}%` }}
                  />
                </div>
              )}
            </>
          ) : (
            <span>
              {scanJob.indexedMemories.toLocaleString()} Memories indexed ·{' '}
              {scanJob.mainImages.toLocaleString()} photos ·{' '}
              {scanJob.mainVideos.toLocaleString()} videos
            </span>
          )}
        </div>
      )}

      <section className="settings-section">
        <div className="settings-section-header">
          <div>
            <p className="eyebrow">New location</p>
            <h3>Add a Memories source</h3>
          </div>
        </div>

        <div className="folder-picker-panel">
          <button
            className="primary-button"
            disabled={isSelectingFolder || isCreating}
            onClick={() => void handleChooseFolder()}
            type="button"
          >
            {isSelectingFolder
              ? 'Opening folder picker…'
              : 'Choose Snapchat export folder'}
          </button>
          <p>
            Choose the parent folder that contains your “memories”, “memories
            2”, and similar export folders.
          </p>
        </div>

        {folderPickerMessage && (
          <div className="state-message manual-path-fallback">
            {folderPickerMessage}
          </div>
        )}

        <form
          className="source-form"
          onSubmit={(event) => void handleCreateSource(event)}
        >
          <label>
            Source name
            <input
              ref={nameInputRef}
              onChange={(event) => setName(event.target.value)}
              placeholder="Snapchat Memories USB"
              value={name}
            />
          </label>

          <label>
            Or enter the folder path manually
            <input
              onChange={(event) => setRootPath(event.target.value)}
              placeholder="/Volumes/MY_USB/snapchat-memories"
              value={rootPath}
            />
          </label>

          <button
            className="primary-button"
            disabled={isCreating}
            type="submit"
          >
            {isCreating ? 'Adding source…' : 'Add source'}
          </button>
        </form>

        <p className="form-hint">
          Select the parent folder containing <code>memories</code>,{' '}
          <code>memories 2</code>, and any later folders.
        </p>
      </section>

      <section className="settings-section">
        <div className="settings-section-header">
          <div>
            <p className="eyebrow">Configured folders</p>
            <h3>Memory sources</h3>
          </div>

          <button
            className="secondary-button"
            disabled={isLoading}
            onClick={handleRefreshSources}
            type="button"
          >
            Refresh
          </button>
        </div>

        {isLoading && (
          <div className="state-message">Loading configured sources…</div>
        )}

        {!isLoading && sources.length === 0 && (
          <div className="state-message">
            No source configured yet. Add your Snapchat export folder above.
          </div>
        )}

        {!isLoading && sources.length > 0 && (
          <div className="source-list">
            {sources.map((source) => {
              const isScanning = scanningSourceId === source.id;
              const isDeleting = deletingSourceId === source.id;
              const isRefreshingAvailability =
                refreshingAvailabilitySourceId === source.id;
              const isUnavailable = source.availabilityStatus !== 'AVAILABLE';
              const sourceStateLabel = getSourceStateLabel(source, isScanning);

              return (
                <article className="source-card" key={source.id}>
                  <div className="source-card-main">
                    <div>
                      <h4>{source.name}</h4>
                      <span className="source-path">Local source folder</span>
                    </div>

                    <span
                      className={`source-status source-status-${(isScanning
                        ? 'RUNNING'
                        : source.availabilityStatus.toLowerCase()
                      ).toLowerCase()}`}
                    >
                      {sourceStateLabel}
                    </span>
                  </div>

                  <div className="source-card-meta">
                    <span>Last scan: {formatDate(source.lastScanAt)}</span>
                    <span>{getStatusLabel(source.lastScanStatus)}</span>
                  </div>

                  {isUnavailable && (
                    <p className="source-availability-message">
                      {source.availabilityMessage}
                    </p>
                  )}

                  <div className="source-card-actions">
                    <button
                      className="primary-button"
                      disabled={isScanning || isDeleting || isUnavailable}
                      onClick={() => void handleScan(source)}
                      type="button"
                    >
                      {isScanning ? 'Scanning…' : 'Scan source'}
                    </button>

                    <button
                      className="secondary-button"
                      disabled={isRefreshingAvailability}
                      onClick={() =>
                        void handleRefreshSourceAvailability(source)
                      }
                      type="button"
                    >
                      {isRefreshingAvailability
                        ? 'Refreshing…'
                        : 'Refresh status'}
                    </button>

                    <button
                      className="danger-button"
                      disabled={isDeleting}
                      onClick={() => void handleDelete(source)}
                      type="button"
                    >
                      {isDeleting ? 'Removing…' : 'Remove'}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </section>
  );
}
