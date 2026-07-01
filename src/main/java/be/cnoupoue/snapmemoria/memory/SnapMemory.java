package be.cnoupoue.snapmemoria.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "memories")
public class SnapMemory {

    @Id
    private String id;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "external_memory_id", nullable = false)
    private String externalMemoryId;

    @Column(name = "captured_at", nullable = false)
    private String capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private SnapMemoryType mediaType;

    @Column(name = "main_path", nullable = false)
    private String mainPath;

    @Column(name = "overlay_path")
    private String overlayPath;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "last_modified_at", nullable = false)
    private String lastModifiedAt;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    protected SnapMemory() {
        // Required by JPA.
    }

    public SnapMemory(
            String id,
            String sourceId,
            String externalMemoryId,
            String capturedAt,
            SnapMemoryType mediaType,
            String mainPath,
            String overlayPath,
            long fileSizeBytes,
            String lastModifiedAt,
            String createdAt,
            String updatedAt
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.externalMemoryId = externalMemoryId;
        this.capturedAt = capturedAt;
        this.mediaType = mediaType;
        this.mainPath = mainPath;
        this.overlayPath = overlayPath;
        this.fileSizeBytes = fileSizeBytes;
        this.lastModifiedAt = lastModifiedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}