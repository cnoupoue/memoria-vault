package be.cnoupoue.memoriavault.source.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.source.FolderPickerUnavailableException;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import be.cnoupoue.memoriavault.source.NativeFolderPicker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NativeFolderPickerApiTests {

  private static final AtomicReference<PickerMode> pickerMode =
      new AtomicReference<>(PickerMode.CANCELLED);
  private static final AtomicReference<Path> pickerPath = new AtomicReference<>();

  @Autowired private MockMvc mockMvc;

  @Autowired private MemorySourceRepository memorySourceRepository;

  @Autowired private MemoryScanJobRepository memoryScanJobRepository;

  @TempDir private Path temporaryDirectory;

  @BeforeEach
  void cleanDatabase() {
    memoryScanJobRepository.deleteAll();
    memorySourceRepository.deleteAll();
    pickerMode.set(PickerMode.CANCELLED);
    pickerPath.set(null);
  }

  @AfterEach
  void resetPicker() {
    pickerMode.set(PickerMode.CANCELLED);
    pickerPath.set(null);
  }

  @Test
  void cancellationReturnsUnselectedResponse() throws Exception {
    pickerMode.set(PickerMode.CANCELLED);

    mockMvc
        .perform(post("/api/sources/select-folder"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selected").value(false))
        .andExpect(jsonPath("$.path").doesNotExist())
        .andExpect(jsonPath("$.name").doesNotExist());
  }

  @Test
  void unavailablePickerReturnsStructuredApiError() throws Exception {
    pickerMode.set(PickerMode.UNAVAILABLE);

    mockMvc
        .perform(post("/api/sources/select-folder"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("FOLDER_PICKER_UNAVAILABLE"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Folder selection is unavailable in this environment. Enter the folder path manually."))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  void selectedFolderDoesNotCreateSourceOrScanJob() throws Exception {
    Path selectedFolder = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));

    pickerMode.set(PickerMode.SELECTED);
    pickerPath.set(selectedFolder);

    mockMvc
        .perform(post("/api/sources/select-folder"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selected").value(true))
        .andExpect(jsonPath("$.path").value(selectedFolder.toAbsolutePath().normalize().toString()))
        .andExpect(jsonPath("$.name").value("snapchat-memories"));

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.count()).isZero();
    org.assertj.core.api.Assertions.assertThat(memoryScanJobRepository.count()).isZero();
  }

  @Test
  void requestBodyCannotProvideArbitraryPath() throws Exception {
    Path selectedFolder = Files.createDirectory(temporaryDirectory.resolve("chosen-by-picker"));

    pickerMode.set(PickerMode.SELECTED);
    pickerPath.set(selectedFolder);

    mockMvc
        .perform(
            post("/api/sources/select-folder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"/tmp/not-selected-by-picker\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selected").value(true))
        .andExpect(
            jsonPath("$.path").value(selectedFolder.toAbsolutePath().normalize().toString()));
  }

  @TestConfiguration
  static class NativeFolderPickerTestConfiguration {

    @Bean
    @Primary
    NativeFolderPicker testNativeFolderPicker() {
      return () -> {
        if (pickerMode.get() == PickerMode.UNAVAILABLE) {
          throw new FolderPickerUnavailableException();
        }

        if (pickerMode.get() == PickerMode.CANCELLED) {
          return Optional.empty();
        }

        return Optional.of(pickerPath.get());
      };
    }
  }

  private enum PickerMode {
    CANCELLED,
    SELECTED,
    UNAVAILABLE
  }
}
