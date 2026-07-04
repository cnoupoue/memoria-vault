package be.cnoupoue.memoriavault.config;

import java.nio.file.Files;
import java.nio.file.Path;

public class ApplicationDataDirectoryResolver {

  public static final String CANONICAL_DIRECTORY_NAME = ".memoria-vault";
  public static final String LEGACY_DIRECTORY_NAME = ".snapmemoria";
  public static final String CANONICAL_DATABASE_FILE_NAME = "memoriavault.db";
  public static final String LEGACY_DATABASE_FILE_NAME = "snapmemoria.db";

  private final Path userHome;

  public ApplicationDataDirectoryResolver(Path userHome) {
    this.userHome = userHome;
  }

  public ApplicationDataDirectories resolve() {
    Path canonicalRoot = userHome.resolve(CANONICAL_DIRECTORY_NAME);
    Path legacyRoot = userHome.resolve(LEGACY_DIRECTORY_NAME);

    if (!Files.exists(canonicalRoot) && Files.exists(legacyRoot)) {
      return new ApplicationDataDirectories(
          legacyRoot,
          legacyRoot.resolve("data").resolve(LEGACY_DATABASE_FILE_NAME),
          legacyRoot.resolve("cache").resolve("thumbnails"),
          true);
    }

    return new ApplicationDataDirectories(
        canonicalRoot,
        canonicalRoot.resolve("data").resolve(CANONICAL_DATABASE_FILE_NAME),
        canonicalRoot.resolve("cache").resolve("thumbnails"),
        false);
  }

  public record ApplicationDataDirectories(
      Path root, Path databasePath, Path thumbnailDirectory, boolean usingLegacyDirectory) {}
}
