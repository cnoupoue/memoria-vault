package be.cnoupoue.snapmemoria.indexing;

import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import be.cnoupoue.snapmemoria.source.api.ScanMemorySourceResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

@Service
public class MemorySourceScanner {

    private final MemorySourceRepository memorySourceRepository;

    public MemorySourceScanner(MemorySourceRepository memorySourceRepository) {
        this.memorySourceRepository = memorySourceRepository;
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
        ScanCounters counters = new ScanCounters();

        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> classify(path, counters));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not scan the configured source folder.",
                    exception
            );
        }

        Instant completedAt = Instant.now();

        return new ScanMemorySourceResponse(
                source.getId(),
                rootPath.toString(),
                "COMPLETED",
                counters.filesVisited,
                counters.mainImages,
                counters.mainVideos,
                counters.overlays,
                counters.unsupportedFiles,
                startedAt.toString(),
                completedAt.toString()
        );
    }

    private void classify(Path filePath, ScanCounters counters) {
        counters.filesVisited++;

        String fileName = filePath.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (fileName.endsWith("-main.jpg")
                || fileName.endsWith("-main.jpeg")) {
            counters.mainImages++;
            return;
        }

        if (fileName.endsWith("-main.mp4")
                || fileName.endsWith("-main.mov")) {
            counters.mainVideos++;
            return;
        }

        if (fileName.endsWith("-overlay.png")) {
            counters.overlays++;
            return;
        }

        counters.unsupportedFiles++;
    }

    private static class ScanCounters {
        private long filesVisited;
        private long mainImages;
        private long mainVideos;
        private long overlays;
        private long unsupportedFiles;
    }
}