package be.cnoupoue.snapmemoria.memory.api;

import be.cnoupoue.snapmemoria.memory.SnapMemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/memories")
public class SnapMemoryController {

    private final SnapMemoryService snapMemoryService;

    public SnapMemoryController(SnapMemoryService snapMemoryService) {
        this.snapMemoryService = snapMemoryService;
    }

    @GetMapping
    public MemoryPageResponse findAll(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size
    ) {
        return snapMemoryService.findAll(year, month, page, size);
    }

    @GetMapping("/{id}")
    public MemoryDetailResponse findById(@PathVariable String id) {
        return snapMemoryService.findById(id);
    }
}