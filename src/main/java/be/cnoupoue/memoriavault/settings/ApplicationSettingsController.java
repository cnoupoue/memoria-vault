package be.cnoupoue.memoriavault.settings;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class ApplicationSettingsController {

  private final ApplicationDataResetService applicationDataResetService;

  public ApplicationSettingsController(ApplicationDataResetService applicationDataResetService) {
    this.applicationDataResetService = applicationDataResetService;
  }

  @PostMapping("/reset-application-data")
  public ApplicationDataResetResponse resetApplicationData() {
    return applicationDataResetService.reset();
  }
}
