package be.cnoupoue.snapmemoria.streaming;

import be.cnoupoue.snapmemoria.memory.SnapMemory;
import be.cnoupoue.snapmemoria.memory.SnapMemoryRepository;
import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MemoryMediaService {

    private final SnapMemoryRepository snapMemoryRepository;
    private final MemorySourceRepository memorySourceRepository;

    public MemoryMediaService(
            SnapMemoryRepository snapMemoryRepository,
            MemorySourceRepository memorySourceRepository
    ) {
        this.snapMemoryRepository = snapMemoryRepository;
        this.memorySourceRepository = memorySourceRepository;
    }

    public FileSystemResource getMainMedia(String memoryId) {
        SnapMemory memory = findMemory(memoryId);

        return createSecureResource(
                memory.getSourceId(),
                memory.getMainPath(),
                "The memory media file is unavailable."
        );
    }

    public FileSystemResource getOverlay(String memoryId) {
        SnapMemory memory = findMemory(memoryId);

        if (memory.getOverlayPath() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "This memory does not have an overlay."
            );
        }

        return createSecureResource(
                memory.getSourceId(),
                memory.getOverlayPath(),
                "The memory overlay file is unavailable."
        );
    }

    private SnapMemory findMemory(String memoryId) {
        return snapMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Memory not found."
                ));
    }

    private FileSystemResource createSecureResource(
            String sourceId,
            String storedMediaPath,
            String unavailableMessage
    ) {
        MemorySource source = memorySourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Memory source not found."
                ));

        try {
            Path sourceRootPath = Path.of(source.getRootPath())
                    .toRealPath();

            Path mediaPath = Path.of(storedMediaPath)
                    .toRealPath();

            if (!mediaPath.startsWith(sourceRootPath)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The requested file is outside the configured memory source."
                );
            }

            if (!Files.isRegularFile(mediaPath) || !Files.isReadable(mediaPath)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        unavailableMessage
                );
            }

            return new FileSystemResource(mediaPath);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    unavailableMessage
            );
        }
    }
}