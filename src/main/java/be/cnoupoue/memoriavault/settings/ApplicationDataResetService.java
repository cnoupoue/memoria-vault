package be.cnoupoue.memoriavault.settings;

import be.cnoupoue.memoriavault.config.ApplicationDataDirectoryResolver;
import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApplicationDataResetService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationDataResetService.class);

  private final MemorySourceRepository memorySourceRepository;
  private final SnapMemoryRepository snapMemoryRepository;
  private final MemoryScanJobRepository memoryScanJobRepository;
  private final Path applicationDataDirectory;
  private final Path databasePath;
  private final Path thumbnailDirectory;
  private final Path playbackDirectory;
  private final Path userHome;

  public ApplicationDataResetService(
      MemorySourceRepository memorySourceRepository,
      SnapMemoryRepository snapMemoryRepository,
      MemoryScanJobRepository memoryScanJobRepository,
      @Value(
              "${memoriavault.data.directory:${user.home}/"
                  + ApplicationDataDirectoryResolver.CANONICAL_DIRECTORY_NAME
                  + "}")
          String applicationDataDirectory,
      @Value("${spring.datasource.url}") String datasourceUrl,
      @Value("${memoriavault.thumbnail.directory}") String thumbnailDirectory,
      @Value("${memoriavault.playback.directory}") String playbackDirectory,
      @Value("${user.home}") String userHome) {
    this.memorySourceRepository = memorySourceRepository;
    this.snapMemoryRepository = snapMemoryRepository;
    this.memoryScanJobRepository = memoryScanJobRepository;
    this.applicationDataDirectory = normalize(Path.of(applicationDataDirectory));
    this.databasePath =
        normalize(resolveSqliteDatabasePath(datasourceUrl, this.applicationDataDirectory));
    this.thumbnailDirectory = normalize(Path.of(thumbnailDirectory));
    this.playbackDirectory = normalize(Path.of(playbackDirectory));
    this.userHome = normalize(Path.of(userHome));
  }

  public ApplicationDataResetResponse reset() {
    if (memoryScanJobRepository.existsByStatus("RUNNING")) {
      throw new ApplicationDataResetException(
          "APPLICATION_RESET_SCAN_RUNNING",
          "A scan is still running. Wait for it to finish before resetting application data.");
    }

    List<Path> sourceRoots =
        memorySourceRepository.findAll().stream()
            .map(MemorySource::getRootPath)
            .map(Path::of)
            .map(ApplicationDataResetService::normalize)
            .toList();

    clearDatabaseState();

    List<Path> resetTargets = buildResetTargets();
    List<String> removedLocations = new ArrayList<>();

    for (Path resetTarget : resetTargets) {
      removedLocations.addAll(deleteApplicationOwnedTarget(resetTarget, sourceRoots));
    }

    recreateRuntimeDirectories();

    return new ApplicationDataResetResponse(
        true,
        false,
        removedLocations,
        "Application data was reset. Your original photos and videos were not deleted.");
  }

  @Transactional
  protected void clearDatabaseState() {
    memoryScanJobRepository.deleteAllInBatch();
    snapMemoryRepository.deleteAllInBatch();
    memorySourceRepository.deleteAllInBatch();
  }

  private List<Path> buildResetTargets() {
    Set<Path> targets = new LinkedHashSet<>();

    targets.add(thumbnailDirectory);
    targets.add(playbackDirectory);
    targets.add(applicationDataDirectory.resolve("cache"));
    targets.add(applicationDataDirectory.resolve("metadata"));
    targets.add(applicationDataDirectory.resolve("logs"));
    targets.add(applicationDataDirectory);

    Path canonicalRoot =
        userHome.resolve(ApplicationDataDirectoryResolver.CANONICAL_DIRECTORY_NAME);
    Path legacyRoot = userHome.resolve(ApplicationDataDirectoryResolver.LEGACY_DIRECTORY_NAME);

    targets.add(canonicalRoot);
    targets.add(legacyRoot);

    return targets.stream().map(ApplicationDataResetService::normalize).distinct().toList();
  }

  private List<String> deleteApplicationOwnedTarget(Path target, List<Path> sourceRoots) {
    if (!Files.exists(target)) {
      return List.of();
    }

    if (isSourcePathProtected(target, sourceRoots)) {
      LOGGER.warn("Skipping reset target {} because it matches a configured source path.", target);
      return List.of();
    }

    if (target.equals(applicationDataDirectory)) {
      return deleteDirectoryContents(target, sourceRoots, Set.of(databasePath));
    }

    if (target.equals(databasePath.getParent())) {
      return deleteDirectoryContents(target, sourceRoots, Set.of(databasePath));
    }

    return deletePath(target, sourceRoots, Set.of(databasePath));
  }

  private List<String> deleteDirectoryContents(
      Path directory, List<Path> sourceRoots, Set<Path> protectedPaths) {
    if (!Files.isDirectory(directory)) {
      return deletePath(directory, sourceRoots, protectedPaths);
    }

    List<String> removedLocations = new ArrayList<>();

    try (var children = Files.list(directory)) {
      for (Path child : children.toList()) {
        removedLocations.addAll(deletePath(normalize(child), sourceRoots, protectedPaths));
      }
    } catch (IOException exception) {
      LOGGER.warn(
          "Could not list application data directory during reset: {}.", directory, exception);
      throw new ApplicationDataResetException(
          "APPLICATION_RESET_FAILED", "Application data could not be reset.", exception);
    }

    return removedLocations;
  }

  private List<String> deletePath(Path path, List<Path> sourceRoots, Set<Path> protectedPaths) {
    Path normalizedPath = normalize(path);

    if (!Files.exists(normalizedPath)) {
      return List.of();
    }

    if (isProtectedPath(normalizedPath, sourceRoots, protectedPaths)) {
      LOGGER.warn("Skipping protected path during application data reset: {}.", normalizedPath);
      return List.of();
    }

    List<Path> paths;

    try (var walk = Files.walk(normalizedPath)) {
      paths =
          walk.map(ApplicationDataResetService::normalize)
              .sorted(Comparator.reverseOrder())
              .toList();
    } catch (IOException exception) {
      LOGGER.warn(
          "Could not enumerate application data path during reset: {}.", normalizedPath, exception);
      throw new ApplicationDataResetException(
          "APPLICATION_RESET_FAILED", "Application data could not be reset.", exception);
    }

    List<String> removedLocations = new ArrayList<>();

    for (Path candidate : paths) {
      if (isProtectedPath(candidate, sourceRoots, protectedPaths)) {
        LOGGER.warn("Skipping protected path during application data reset: {}.", candidate);
        continue;
      }

      try {
        boolean deleted = Files.deleteIfExists(candidate);

        if (deleted) {
          removedLocations.add(candidate.toString());
        }
      } catch (IOException exception) {
        LOGGER.warn(
            "Could not delete application data path during reset: {}.", candidate, exception);
        throw new ApplicationDataResetException(
            "APPLICATION_RESET_FAILED", "Application data could not be reset.", exception);
      }
    }

    return removedLocations;
  }

  private void recreateRuntimeDirectories() {
    try {
      Files.createDirectories(databasePath.getParent());
      Files.createDirectories(thumbnailDirectory);
      Files.createDirectories(playbackDirectory);
    } catch (IOException exception) {
      LOGGER.warn("Could not recreate application data directories after reset.", exception);
      throw new ApplicationDataResetException(
          "APPLICATION_RESET_FAILED",
          "Application data was reset, but local directories could not be recreated.",
          exception);
    }
  }

  private boolean isProtectedPath(Path path, List<Path> sourceRoots, Set<Path> protectedPaths) {
    if (protectedPaths.stream()
        .anyMatch(protectedPath -> path.equals(protectedPath) || protectedPath.startsWith(path))) {
      return true;
    }

    return isSourcePathProtected(path, sourceRoots);
  }

  private boolean isSourcePathProtected(Path path, List<Path> sourceRoots) {
    return sourceRoots.stream()
        .anyMatch(
            sourceRoot ->
                path.equals(sourceRoot)
                    || path.startsWith(sourceRoot)
                    || sourceRoot.startsWith(path));
  }

  private static Path resolveSqliteDatabasePath(String datasourceUrl, Path fallbackRoot) {
    String sqlitePrefix = "jdbc:sqlite:";

    if (datasourceUrl != null && datasourceUrl.startsWith(sqlitePrefix)) {
      return Path.of(datasourceUrl.substring(sqlitePrefix.length()));
    }

    return fallbackRoot
        .resolve("data")
        .resolve(ApplicationDataDirectoryResolver.CANONICAL_DATABASE_FILE_NAME);
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
