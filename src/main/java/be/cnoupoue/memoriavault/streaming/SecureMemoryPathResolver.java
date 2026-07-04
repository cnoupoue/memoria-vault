package be.cnoupoue.memoriavault.streaming;

import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SecureMemoryPathResolver {

  private final MemorySourceRepository memorySourceRepository;

  public SecureMemoryPathResolver(MemorySourceRepository memorySourceRepository) {
    this.memorySourceRepository = memorySourceRepository;
  }

  public Path resolve(String sourceId, String storedMediaPath, String unavailableMessage) {
    MemorySource source =
        memorySourceRepository
            .findById(sourceId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory source not found."));

    try {
      Path sourceRootPath = Path.of(source.getRootPath()).toRealPath();
      Path storedPath = Path.of(storedMediaPath);

      Optional<Path> rebasedPath = resolveRebasedPath(storedPath, sourceRootPath);

      if (rebasedPath.isPresent()) {
        return rebasedPath.get();
      }

      Optional<Path> directPath = resolveDirectPath(storedPath, sourceRootPath);

      if (directPath.isPresent()) {
        return directPath.get();
      }
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, unavailableMessage);
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, unavailableMessage);
  }

  private Optional<Path> resolveDirectPath(Path storedPath, Path sourceRootPath)
      throws IOException {
    try {
      Path mediaPath = storedPath.toRealPath();

      if (!mediaPath.startsWith(sourceRootPath)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "The requested file is outside the configured memory source.");
      }

      return readableRegularFile(mediaPath);
    } catch (IOException exception) {
      return Optional.empty();
    }
  }

  private Optional<Path> resolveRebasedPath(Path storedPath, Path sourceRootPath)
      throws IOException {
    Optional<Path> relativePath = toSnapchatExportRelativePath(storedPath);

    if (relativePath.isEmpty() && !storedPath.isAbsolute()) {
      relativePath = Optional.of(storedPath.normalize());
    }

    if (relativePath.isEmpty()) {
      return Optional.empty();
    }

    Path mediaPath;

    try {
      mediaPath = sourceRootPath.resolve(relativePath.get()).normalize().toRealPath();
    } catch (IOException exception) {
      return Optional.empty();
    }

    if (!mediaPath.startsWith(sourceRootPath)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The requested file is outside the configured memory source.");
    }

    return readableRegularFile(mediaPath);
  }

  private Optional<Path> toSnapchatExportRelativePath(Path storedPath) {
    int nameCount = storedPath.getNameCount();

    for (int index = 0; index < nameCount; index++) {
      String segment = storedPath.getName(index).toString().toLowerCase(Locale.ROOT);

      if (segment.matches("memories(?: \\d+)?")) {
        return Optional.of(storedPath.subpath(index, nameCount));
      }
    }

    return Optional.empty();
  }

  private Optional<Path> readableRegularFile(Path mediaPath) {
    if (!Files.isRegularFile(mediaPath) || !Files.isReadable(mediaPath)) {
      return Optional.empty();
    }

    return Optional.of(mediaPath);
  }
}
