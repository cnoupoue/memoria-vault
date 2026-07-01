import {type FormEvent, useEffect, useState } from "react";
import {
    createMemorySource,
    deleteMemorySource,
    getMemorySources,
    scanMemorySource,
} from "../api/snapmemoriaApi";
import type {
    MemorySource,
    ScanMemorySourceResponse,
} from "../api/types";

function formatDate(value: string | null): string {
    if (!value) {
        return "Never";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(date);
}

function getStatusLabel(status: string | null): string {
    if (!status) {
        return "Not scanned";
    }

    return status
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

type SettingsPageProps = {
    onSourceScanned: () => void;
};

export function SettingsPage({
                                 onSourceScanned,
                             }: SettingsPageProps) {
    const [sources, setSources] = useState<MemorySource[]>([]);
    const [name, setName] = useState("");
    const [rootPath, setRootPath] = useState("");

    const [isLoading, setIsLoading] = useState(true);
    const [isCreating, setIsCreating] = useState(false);
    const [scanningSourceId, setScanningSourceId] = useState<string | null>(
        null,
    );
    const [deletingSourceId, setDeletingSourceId] = useState<string | null>(
        null,
    );

    const [error, setError] = useState<string | null>(null);
    const [scanResult, setScanResult] =
        useState<ScanMemorySourceResponse | null>(null);

    async function loadSources() {
        setIsLoading(true);
        setError(null);

        try {
            const data = await getMemorySources();
            setSources(data);
        } catch {
            setError(
                "Could not load memory sources. Check that the backend is running.",
            );
        } finally {
            setIsLoading(false);
        }
    }

    useEffect(() => {
        void loadSources();
    }, []);

    async function handleCreateSource(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!name.trim() || !rootPath.trim()) {
            setError("A source name and folder path are required.");
            return;
        }

        setIsCreating(true);
        setError(null);
        setScanResult(null);

        try {
            const createdSource = await createMemorySource({
                name: name.trim(),
                rootPath: rootPath.trim(),
            });

            setSources((currentSources) => [
                ...currentSources,
                createdSource,
            ]);

            setName("");
            setRootPath("");
        } catch {
            setError(
                "Could not add this source. It may already exist or the path may be invalid.",
            );
        } finally {
            setIsCreating(false);
        }
    }

    async function handleScan(source: MemorySource) {
        setScanningSourceId(source.id);
        setError(null);
        setScanResult(null);

        try {
            const result = await scanMemorySource(source.id);

            setScanResult(result);

            await loadSources();
            onSourceScanned();
        } catch {
            setError(
                "Could not scan this source. Check that the drive is connected and the configured folder is available.",
            );
        } finally {
            setScanningSourceId(null);
        }
    }

    async function handleDelete(source: MemorySource) {
        const confirmed = window.confirm(
            `Remove "${source.name}" from SnapMemoria?\n\nThis only removes the configured source. It will not delete any files from your drive.`,
        );

        if (!confirmed) {
            return;
        }

        setDeletingSourceId(source.id);
        setError(null);
        setScanResult(null);

        try {
            await deleteMemorySource(source.id);

            setSources((currentSources) =>
                currentSources.filter((item) => item.id !== source.id),
            );
        } catch {
            setError("Could not remove this source.");
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

            {scanResult && (
                <div className="scan-result-banner">
                    <strong>Scan completed</strong>
                    <span>
            {scanResult.indexedMemories} Memories indexed ·{" "}
                        {scanResult.mainImages} photos · {scanResult.mainVideos} videos
          </span>
                </div>
            )}

            <section className="settings-section">
                <div className="settings-section-header">
                    <div>
                        <p className="eyebrow">New location</p>
                        <h3>Add a Memories source</h3>
                    </div>
                </div>

                <form
                    className="source-form"
                    onSubmit={(event) => void handleCreateSource(event)}
                >
                    <label>
                        Source name
                        <input
                            onChange={(event) => setName(event.target.value)}
                            placeholder="Snapchat Memories USB"
                            value={name}
                        />
                    </label>

                    <label>
                        Export folder path
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
                        {isCreating ? "Adding source…" : "Add source"}
                    </button>
                </form>

                <p className="form-hint">
                    Select the parent folder containing <code>memories</code>,{" "}
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
                        onClick={() => void loadSources()}
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

                            return (
                                <article className="source-card" key={source.id}>
                                    <div className="source-card-main">
                                        <div>
                                            <h4>{source.name}</h4>
                                            <code className="source-path">{source.rootPath}</code>
                                        </div>

                                        <span
                                            className={`source-status source-status-${(
                                                source.lastScanStatus ?? "NOT_SCANNED"
                                            ).toLowerCase()}`}
                                        >
                      {getStatusLabel(source.lastScanStatus)}
                    </span>
                                    </div>

                                    <div className="source-card-meta">
                                        <span>Last scan: {formatDate(source.lastScanAt)}</span>
                                    </div>

                                    <div className="source-card-actions">
                                        <button
                                            className="primary-button"
                                            disabled={isScanning || isDeleting}
                                            onClick={() => void handleScan(source)}
                                            type="button"
                                        >
                                            {isScanning ? "Scanning…" : "Scan source"}
                                        </button>

                                        <button
                                            className="danger-button"
                                            disabled={isScanning || isDeleting}
                                            onClick={() => void handleDelete(source)}
                                            type="button"
                                        >
                                            {isDeleting ? "Removing…" : "Remove"}
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