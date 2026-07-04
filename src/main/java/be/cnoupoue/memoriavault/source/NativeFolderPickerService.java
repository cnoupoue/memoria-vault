package be.cnoupoue.memoriavault.source;

import be.cnoupoue.memoriavault.source.api.FolderSelectionResponse;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class NativeFolderPickerService {

  private final NativeFolderPicker nativeFolderPicker;
  private final SourceAvailabilityService sourceAvailabilityService;

  public NativeFolderPickerService(
      NativeFolderPicker nativeFolderPicker, SourceAvailabilityService sourceAvailabilityService) {
    this.nativeFolderPicker = nativeFolderPicker;
    this.sourceAvailabilityService = sourceAvailabilityService;
  }

  public FolderSelectionResponse selectFolder() {
    Optional<Path> selectedPath = nativeFolderPicker.selectFolder();

    if (selectedPath.isEmpty()) {
      return new FolderSelectionResponse(false, null, null);
    }

    Path normalizedPath = selectedPath.get().toAbsolutePath().normalize();
    SourceAvailability availability = sourceAvailabilityService.check(normalizedPath);

    if (!availability.isAvailable()) {
      throw new InvalidFolderSelectionException();
    }

    Path fileName = normalizedPath.getFileName();

    return new FolderSelectionResponse(
        true,
        normalizedPath.toString(),
        fileName == null ? normalizedPath.toString() : fileName.toString());
  }
}
