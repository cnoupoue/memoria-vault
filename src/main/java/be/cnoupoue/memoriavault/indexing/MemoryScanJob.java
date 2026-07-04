package be.cnoupoue.memoriavault.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_scan_jobs")
public class MemoryScanJob {

  @Id private String id;

  @Column(name = "source_id", nullable = false)
  private String sourceId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "total_files", nullable = false)
  private long totalFiles;

  @Column(name = "files_processed", nullable = false)
  private long filesProcessed;

  @Column(name = "main_images", nullable = false)
  private long mainImages;

  @Column(name = "main_videos", nullable = false)
  private long mainVideos;

  @Column(name = "overlays", nullable = false)
  private long overlays;

  @Column(name = "indexed_memories", nullable = false)
  private long indexedMemories;

  @Column(name = "attached_overlays", nullable = false)
  private long attachedOverlays;

  @Column(name = "unmatched_overlays", nullable = false)
  private long unmatchedOverlays;

  @Column(name = "unsupported_files", nullable = false)
  private long unsupportedFiles;

  @Column(name = "unreadable_files", nullable = false)
  private long unreadableFiles;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "started_at", nullable = false)
  private String startedAt;

  @Column(name = "completed_at")
  private String completedAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  protected MemoryScanJob() {
    // Required by JPA.
  }

  public MemoryScanJob(String id, String sourceId, String startedAt) {
    this.id = id;
    this.sourceId = sourceId;
    this.status = "RUNNING";

    this.totalFiles = 0;
    this.filesProcessed = 0;
    this.mainImages = 0;
    this.mainVideos = 0;
    this.overlays = 0;
    this.indexedMemories = 0;
    this.attachedOverlays = 0;
    this.unmatchedOverlays = 0;
    this.unsupportedFiles = 0;
    this.unreadableFiles = 0;

    this.startedAt = startedAt;
    this.updatedAt = startedAt;
  }

  public void updateProgress(ScanProgress progress, String updatedAt) {
    this.totalFiles = progress.totalFiles();
    this.filesProcessed = progress.filesProcessed();

    this.mainImages = progress.mainImages();
    this.mainVideos = progress.mainVideos();
    this.overlays = progress.overlays();

    this.indexedMemories = progress.indexedMemories();
    this.attachedOverlays = progress.attachedOverlays();
    this.unmatchedOverlays = progress.unmatchedOverlays();

    this.unsupportedFiles = progress.unsupportedFiles();
    this.unreadableFiles = progress.unreadableFiles();

    this.updatedAt = updatedAt;
  }

  public void markCompleted(ScanProgress progress, String completedAt) {
    updateProgress(progress, completedAt);

    this.status = "COMPLETED";
    this.completedAt = completedAt;
  }

  public void markFailed(String errorMessage, String failedAt) {
    this.status = "FAILED";
    this.errorMessage = errorMessage;
    this.completedAt = failedAt;
    this.updatedAt = failedAt;
  }

  public String getId() {
    return id;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getStatus() {
    return status;
  }

  public long getTotalFiles() {
    return totalFiles;
  }

  public long getFilesProcessed() {
    return filesProcessed;
  }

  public long getMainImages() {
    return mainImages;
  }

  public long getMainVideos() {
    return mainVideos;
  }

  public long getOverlays() {
    return overlays;
  }

  public long getIndexedMemories() {
    return indexedMemories;
  }

  public long getAttachedOverlays() {
    return attachedOverlays;
  }

  public long getUnmatchedOverlays() {
    return unmatchedOverlays;
  }

  public long getUnsupportedFiles() {
    return unsupportedFiles;
  }

  public long getUnreadableFiles() {
    return unreadableFiles;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getStartedAt() {
    return startedAt;
  }

  public String getCompletedAt() {
    return completedAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }
}
