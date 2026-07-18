package be.cnoupoue.memoriavault.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MemoryIndexPersistenceTest {

  @Autowired private MemoryIndexPersistence memoryIndexPersistence;
  @Autowired private SnapMemoryRepository snapMemoryRepository;
  @Autowired private MemorySourceRepository memorySourceRepository;

  @BeforeEach
  void cleanDatabase() {
    snapMemoryRepository.deleteAll();
    memorySourceRepository.deleteAll();
  }

  @Test
  void synchronizesSourceMemoriesByStableExternalIdAndPreservesFavorites() {
    MemorySource source = memorySourceRepository.save(source("source-1"));
    MemorySource otherSource = memorySourceRepository.save(source("source-2"));
    SnapMemory favoriteImage =
        snapMemoryRepository.save(
            memory(
                "favorite-image-row",
                source.getId(),
                "external-image",
                "2024-01-01",
                SnapMemoryType.IMAGE,
                "/old/image.jpg",
                100,
                true,
                "2026-07-18T10:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "favorite-video-row",
            source.getId(),
            "external-video",
            "2023-01-01",
            SnapMemoryType.VIDEO,
            "/old/video.mp4",
            200,
            true,
            "2026-07-18T09:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "missing-favorite-row",
            source.getId(),
            "external-missing",
            "2022-01-01",
            SnapMemoryType.IMAGE,
            "/old/missing.jpg",
            300,
            true,
            "2026-07-18T08:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "other-source-favorite-row",
            otherSource.getId(),
            "external-image",
            "2021-01-01",
            SnapMemoryType.IMAGE,
            "/other/image.jpg",
            400,
            true,
            "2026-07-18T07:00:00Z"));

    memoryIndexPersistence.synchronizeSourceMemories(
        source.getId(),
        List.of(
            memory(
                "new-random-image-row",
                source.getId(),
                "external-image",
                "2024-02-03",
                SnapMemoryType.IMAGE,
                "/new/image.jpg",
                111,
                false,
                null),
            memory(
                "new-random-video-row",
                source.getId(),
                "external-video",
                "2023-02-03",
                SnapMemoryType.VIDEO,
                "/new/video.mp4",
                222,
                false,
                null),
            memory(
                "new-memory-row",
                source.getId(),
                "external-new",
                "2025-02-03",
                SnapMemoryType.IMAGE,
                "/new/new.jpg",
                333,
                false,
                null)));

    List<SnapMemory> sourceMemories = snapMemoryRepository.findBySourceId(source.getId());

    assertThat(sourceMemories)
        .extracting(SnapMemory::getExternalMemoryId)
        .containsExactlyInAnyOrder("external-image", "external-video", "external-new");
    assertThat(snapMemoryRepository.findById("missing-favorite-row")).isEmpty();

    SnapMemory preservedImage = snapMemoryRepository.findById(favoriteImage.getId()).orElseThrow();
    assertThat(preservedImage.getExternalMemoryId()).isEqualTo("external-image");
    assertThat(preservedImage.getCapturedAt()).isEqualTo("2024-02-03");
    assertThat(preservedImage.getMainPath()).isEqualTo("/new/image.jpg");
    assertThat(preservedImage.getFileSizeBytes()).isEqualTo(111);
    assertThat(preservedImage.isFavorite()).isTrue();
    assertThat(preservedImage.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");

    SnapMemory preservedVideo = snapMemoryRepository.findById("favorite-video-row").orElseThrow();
    assertThat(preservedVideo.getMainPath()).isEqualTo("/new/video.mp4");
    assertThat(preservedVideo.isFavorite()).isTrue();
    assertThat(preservedVideo.getFavoritedAt()).isEqualTo("2026-07-18T09:00:00Z");

    SnapMemory newMemory =
        snapMemoryRepository.findBySourceId(source.getId()).stream()
            .filter(memory -> memory.getExternalMemoryId().equals("external-new"))
            .findFirst()
            .orElseThrow();
    assertThat(newMemory.isFavorite()).isFalse();
    assertThat(newMemory.getFavoritedAt()).isNull();

    SnapMemory otherSourceFavorite =
        snapMemoryRepository.findById("other-source-favorite-row").orElseThrow();
    assertThat(otherSourceFavorite.isFavorite()).isTrue();
    assertThat(otherSourceFavorite.getMainPath()).isEqualTo("/other/image.jpg");
  }

  private MemorySource source(String id) {
    String now = Instant.now().toString();

    return new MemorySource(id, "Source " + id, "/tmp/" + id, null, "NOT_SCANNED", now, now);
  }

  private SnapMemory memory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
      long fileSizeBytes,
      boolean isFavorite,
      String favoritedAt) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        sourceId,
        externalMemoryId,
        capturedAt,
        mediaType,
        mainPath,
        null,
        fileSizeBytes,
        now,
        now,
        now,
        isFavorite,
        favoritedAt);
  }
}
