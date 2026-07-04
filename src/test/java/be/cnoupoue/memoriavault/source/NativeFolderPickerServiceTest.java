package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.source.api.FolderSelectionResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NativeFolderPickerServiceTest {

  @Mock private NativeFolderPicker nativeFolderPicker;

  @TempDir private Path temporaryDirectory;

  @Test
  void returnsCancelledResponseWhenPickerReturnsNoPath() {
    when(nativeFolderPicker.selectFolder()).thenReturn(Optional.empty());

    NativeFolderPickerService service =
        new NativeFolderPickerService(nativeFolderPicker, new SourceAvailabilityService());

    FolderSelectionResponse response = service.selectFolder();

    assertThat(response.selected()).isFalse();
    assertThat(response.path()).isNull();
    assertThat(response.name()).isNull();
  }

  @Test
  void returnsNormalizedSelectedFolder() throws Exception {
    Path selectedFolder = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));
    when(nativeFolderPicker.selectFolder()).thenReturn(Optional.of(selectedFolder.resolve(".")));

    NativeFolderPickerService service =
        new NativeFolderPickerService(nativeFolderPicker, new SourceAvailabilityService());

    FolderSelectionResponse response = service.selectFolder();

    assertThat(response.selected()).isTrue();
    assertThat(response.path()).isEqualTo(selectedFolder.toAbsolutePath().normalize().toString());
    assertThat(response.name()).isEqualTo("snapchat-memories");
  }

  @Test
  void rejectsUnavailableSelectedFolder() {
    Path missingFolder = temporaryDirectory.resolve("missing");
    when(nativeFolderPicker.selectFolder()).thenReturn(Optional.of(missingFolder));

    NativeFolderPickerService service =
        new NativeFolderPickerService(nativeFolderPicker, new SourceAvailabilityService());

    assertThatThrownBy(service::selectFolder).isInstanceOf(InvalidFolderSelectionException.class);
  }
}
