package be.cnoupoue.memoriavault.playback;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
public class PlaybackController {

  private final CompatibilityPlaybackService compatibilityPlaybackService;
  private final OriginalFileOpenService originalFileOpenService;

  public PlaybackController(
      CompatibilityPlaybackService compatibilityPlaybackService,
      OriginalFileOpenService originalFileOpenService) {
    this.compatibilityPlaybackService = compatibilityPlaybackService;
    this.originalFileOpenService = originalFileOpenService;
  }

  @PostMapping("/{id}/playback/compatible")
  public CompatibilityPlaybackResponse prepareCompatibilityPlayback(@PathVariable String id) {
    return compatibilityPlaybackService.prepareCompatibilityPlayback(id);
  }

  @PostMapping("/{id}/open-original")
  public OriginalFileOpenResponse openOriginal(@PathVariable String id) {
    originalFileOpenService.openOriginal(id);

    return new OriginalFileOpenResponse(true, "The original file was opened locally.");
  }
}
