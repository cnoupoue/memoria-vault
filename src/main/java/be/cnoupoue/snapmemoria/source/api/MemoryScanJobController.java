package be.cnoupoue.snapmemoria.source.api;

import be.cnoupoue.snapmemoria.indexing.MemoryScanJob;
import be.cnoupoue.snapmemoria.indexing.MemoryScanJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scans")
public class MemoryScanJobController {

    private final MemoryScanJobService memoryScanJobService;

    public MemoryScanJobController(
            MemoryScanJobService memoryScanJobService
    ) {
        this.memoryScanJobService = memoryScanJobService;
    }

    @GetMapping("/{id}")
    public MemoryScanJobResponse findById(@PathVariable String id) {
        return toResponse(memoryScanJobService.findById(id));
    }

    @GetMapping("/latest/source/{sourceId}")
    public MemoryScanJobResponse findLatestForSource(
            @PathVariable String sourceId
    ) {
        return toResponse(
                memoryScanJobService.findLatestBySourceId(sourceId)
        );
    }

    static MemoryScanJobResponse toResponse(MemoryScanJob scanJob) {
        return new MemoryScanJobResponse(
                scanJob.getId(),
                scanJob.getSourceId(),
                scanJob.getStatus(),

                scanJob.getTotalFiles(),
                scanJob.getFilesProcessed(),

                scanJob.getMainImages(),
                scanJob.getMainVideos(),
                scanJob.getOverlays(),

                scanJob.getIndexedMemories(),
                scanJob.getAttachedOverlays(),
                scanJob.getUnmatchedOverlays(),

                scanJob.getUnsupportedFiles(),
                scanJob.getUnreadableFiles(),

                scanJob.getErrorMessage(),

                scanJob.getStartedAt(),
                scanJob.getCompletedAt(),
                scanJob.getUpdatedAt()
        );
    }
}