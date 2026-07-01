package be.cnoupoue.snapmemoria.source.api;

import be.cnoupoue.snapmemoria.source.MemorySourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import be.cnoupoue.snapmemoria.indexing.MemorySourceScanner;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class MemorySourceController {

    private final MemorySourceService memorySourceService;
    private final MemorySourceScanner memorySourceScanner;


    public MemorySourceController(
            MemorySourceService memorySourceService,
            MemorySourceScanner memorySourceScanner
    ) {
        this.memorySourceService = memorySourceService;
        this.memorySourceScanner = memorySourceScanner;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public MemorySourceResponse create(
            @Valid @RequestBody CreateMemorySourceRequest request
    ) {
        return memorySourceService.create(request);
    }

    @GetMapping
    public List<MemorySourceResponse> findAll() {
        return memorySourceService.findAll();
    }

    @PostMapping("/{id}/scan")
    public ScanMemorySourceResponse scan(@PathVariable String id) {
        return memorySourceScanner.scan(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        memorySourceService.delete(id);
    }
}