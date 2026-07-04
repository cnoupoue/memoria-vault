package be.cnoupoue.memoriavault.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationDataDirectoryResolverTest {

  @TempDir private Path userHome;

  @Test
  void selectsCanonicalDirectoryForNewInstallations() {
    ApplicationDataDirectoryResolver.ApplicationDataDirectories directories =
        new ApplicationDataDirectoryResolver(userHome).resolve();

    assertThat(directories.root()).isEqualTo(userHome.resolve(".memoria-vault"));
    assertThat(directories.databasePath())
        .isEqualTo(userHome.resolve(".memoria-vault/data/memoriavault.db"));
    assertThat(directories.thumbnailDirectory())
        .isEqualTo(userHome.resolve(".memoria-vault/cache/thumbnails"));
    assertThat(directories.usingLegacyDirectory()).isFalse();
  }

  @Test
  void detectsExistingLegacyDirectoryWhenCanonicalDirectoryIsAbsent() throws Exception {
    Files.createDirectories(userHome.resolve(".snapmemoria/data"));
    Files.writeString(userHome.resolve(".snapmemoria/data/snapmemoria.db"), "existing db");
    Files.createDirectories(userHome.resolve(".snapmemoria/cache/thumbnails"));

    ApplicationDataDirectoryResolver.ApplicationDataDirectories directories =
        new ApplicationDataDirectoryResolver(userHome).resolve();

    assertThat(directories.root()).isEqualTo(userHome.resolve(".snapmemoria"));
    assertThat(directories.databasePath())
        .isEqualTo(userHome.resolve(".snapmemoria/data/snapmemoria.db"));
    assertThat(directories.thumbnailDirectory())
        .isEqualTo(userHome.resolve(".snapmemoria/cache/thumbnails"));
    assertThat(directories.usingLegacyDirectory()).isTrue();
    assertThat(Files.readString(userHome.resolve(".snapmemoria/data/snapmemoria.db")))
        .isEqualTo("existing db");
  }

  @Test
  void existingCanonicalDirectoryTakesPrecedenceAndLegacyDataIsNotOverwritten() throws Exception {
    Files.createDirectories(userHome.resolve(".memoria-vault/data"));
    Files.writeString(userHome.resolve(".memoria-vault/data/memoriavault.db"), "new db");
    Files.createDirectories(userHome.resolve(".snapmemoria/data"));
    Files.writeString(userHome.resolve(".snapmemoria/data/snapmemoria.db"), "legacy db");

    ApplicationDataDirectoryResolver.ApplicationDataDirectories directories =
        new ApplicationDataDirectoryResolver(userHome).resolve();

    assertThat(directories.root()).isEqualTo(userHome.resolve(".memoria-vault"));
    assertThat(directories.databasePath())
        .isEqualTo(userHome.resolve(".memoria-vault/data/memoriavault.db"));
    assertThat(directories.usingLegacyDirectory()).isFalse();
    assertThat(Files.readString(userHome.resolve(".snapmemoria/data/snapmemoria.db")))
        .isEqualTo("legacy db");
  }
}
