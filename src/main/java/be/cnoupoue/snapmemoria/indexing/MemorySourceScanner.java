package be.cnoupoue.snapmemoria.indexing;

import be.cnoupoue.snapmemoria.memory.SnapMemory;
import be.cnoupoue.snapmemoria.memory.SnapMemoryType;
import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import be.cnoupoue.snapmemoria.source.api.ScanMemorySourceResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MemorySourceScanner {

    private static final int BATCH_SIZE = 500;

    private static final Pattern SNAPCHAT_FILE_PATTERN = Pattern.compile(
            "^(?<date>\\d{4}-\\d{2}-\\d{2})_"
                    + "(?<memoryId>[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})-"
                    + "(?<asset>main|overlay)\\."
                    + "(?<extension>jpg|jpeg|png|mp4|mov)$",
            Pattern.CASE_INSENSITIVE
    );

    private final MemorySourceRepository memorySourceRepository;
    private final MemoryIndexPersistence memoryIndexPersistence;

    public MemorySourceScanner(
            MemorySourceRepository memorySourceRepository,
            MemoryIndexPersistence memoryIndexPersistence
    ) {
        this.memorySourceRepository = memorySourceRepository;
        this.memoryIndexPersistence = memoryIndexPersistence;
    }

    public ScanMemorySourceResponse scan(String sourceId) {
        MemorySource source = memorySourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Memory source not found."));

        Path rootPath = Path.of(source.getRootPath());

        if (!Files.exists(rootPath)) {
            throw new IllegalStateException(
                    "The configured source folder is currently unavailable: " + rootPath
            );
        }

        if (!Files.isDirectory(rootPath)) {
            throw new IllegalStateException(
                    "The configured source path is not a directory: " + rootPath
            );
        }

        Instant startedAt = Instant.now();
        source.markScanStarted(startedAt.toString());
        memorySourceRepository.save(source);

        ScanCounters counters = new ScanCounters();

        try {
            /*
             * A rescan replaces the existing index for this source.
             * Original media files are never touched.
             */
            memoryIndexPersistence.deleteBySourceId(sourceId);

            indexMainFiles(source, rootPath, counters);

            Instant completedAt = Instant.now();

            source.markScanCompleted(completedAt.toString());
            memorySourceRepository.save(source);

            return new ScanMemorySourceResponse(
                    source.getId(),
                    rootPath.toString(),
                    "COMPLETED",
                    counters.filesVisited,
                    counters.mainImages,
                    counters.mainVideos,
                    counters.overlays,
                    counters.indexedMemories,
                    counters.attachedOverlays,
                    counters.unmatchedOverlays,
                    counters.unsupportedFiles,
                    counters.unreadableFiles,
                    startedAt.toString(),
                    completedAt.toString()
            );
        } catch (RuntimeException exception) {
            source.markScanFailed(Instant.now().toString());
            memorySourceRepository.save(source);
            throw exception;
        }
    }

    private void indexMainFiles(
            MemorySource source,
            Path rootPath,
            ScanCounters counters
    ) {
        List<SnapMemory> batch = new ArrayList<>(BATCH_SIZE);

        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> processMainFile(source, path, batch, counters));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not scan the configured source folder.",
                    exception
            );
        }

        saveBatchIfNeeded(batch);
    }

    private void processMainFile(
            MemorySource source,
            Path filePath,
            List<SnapMemory> batch,
            ScanCounters counters
    ) {
        counters.filesVisited++;

        ParsedSnapchatAsset asset = parseSnapchatAsset(filePath);

        if (asset == null) {
            counters.unsupportedFiles++;
            return;
        }

        if (asset.isOverlay()) {
            counters.overlays++;
            return;
        }

        if (asset.mediaType() == SnapMemoryType.IMAGE) {
            counters.mainImages++;
        } else {
            counters.mainVideos++;
        }

        SnapMemory memory = createMemory(source, filePath, asset, counters);

        if (memory == null) {
            return;
        }

        batch.add(memory);
        counters.indexedMemories++;

        if (memory.getOverlayPath() != null) {
            counters.attachedOverlays++;
        }

        if (batch.size() >= BATCH_SIZE) {
            memoryIndexPersistence.saveBatch(List.copyOf(batch));
            batch.clear();
        }
    }

    private SnapMemory createMemory(
            MemorySource source,
            Path filePath,
            ParsedSnapchatAsset asset,
            ScanCounters counters
    ) {
        try {
            String now = Instant.now().toString();

            Path overlayPath = findSiblingOverlay(filePath);

            return new SnapMemory(
                    UUID.randomUUID().toString(),
                    source.getId(),
                    asset.memoryId(),
                    asset.capturedAt(),
                    asset.mediaType(),
                    filePath.toAbsolutePath().normalize().toString(),
                    overlayPath == null ? null : overlayPath.toString(),
                    Files.size(filePath),
                    Files.getLastModifiedTime(filePath).toInstant().toString(),
                    now,
                    now
            );
        } catch (IOException exception) {
            counters.unreadableFiles++;
            return null;
        }
    }

    private ParsedSnapchatAsset parseSnapchatAsset(Path filePath) {
        String fileName = filePath.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        Matcher matcher = SNAPCHAT_FILE_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            return null;
        }

        String capturedAt = matcher.group("date");
        String memoryId = matcher.group("memoryId");
        String assetType = matcher.group("asset");
        String extension = matcher.group("extension");

        try {
            LocalDate.parse(capturedAt);
        } catch (RuntimeException exception) {
            return null;
        }

        if ("overlay".equals(assetType)) {
            if (!"png".equals(extension)) {
                return null;
            }

            return new ParsedSnapchatAsset(
                    capturedAt,
                    memoryId,
                    true,
                    null
            );
        }

        SnapMemoryType mediaType = switch (extension) {
            case "jpg", "jpeg" -> SnapMemoryType.IMAGE;
            case "mp4", "mov" -> SnapMemoryType.VIDEO;
            default -> null;
        };

        if (mediaType == null) {
            return null;
        }

        return new ParsedSnapchatAsset(
                capturedAt,
                memoryId,
                false,
                mediaType
        );
    }

    private void saveBatchIfNeeded(List<SnapMemory> batch) {
        if (!batch.isEmpty()) {
            memoryIndexPersistence.saveBatch(List.copyOf(batch));
            batch.clear();
        }
    }

    private Path findSiblingOverlay(Path mainFilePath) {
        String mainFileName = mainFilePath.getFileName().toString();

        String overlayFileName = mainFileName.replaceFirst(
                "(?i)-main\\.(jpg|jpeg|mp4|mov)$",
                "-overlay.png"
        );

        if (overlayFileName.equals(mainFileName)) {
            return null;
        }

        Path candidate = mainFilePath
                .resolveSibling(overlayFileName)
                .toAbsolutePath()
                .normalize();

        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private record ParsedSnapchatAsset(
            String capturedAt,
            String memoryId,
            boolean isOverlay,
            SnapMemoryType mediaType
    ) {
    }

    private static class ScanCounters {
        private long filesVisited;
        private long mainImages;
        private long mainVideos;
        private long overlays;
        private long indexedMemories;
        private long attachedOverlays;
        private long unmatchedOverlays;
        private long unsupportedFiles;
        private long unreadableFiles;
    }
}