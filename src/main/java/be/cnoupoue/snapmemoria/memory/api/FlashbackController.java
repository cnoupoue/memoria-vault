package be.cnoupoue.snapmemoria.memory.api;

import be.cnoupoue.snapmemoria.memory.SnapMemoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/flashbacks")
public class FlashbackController {

    private final SnapMemoryService snapMemoryService;

    public FlashbackController(SnapMemoryService snapMemoryService) {
        this.snapMemoryService = snapMemoryService;
    }

    @GetMapping("/today")
    public FlashbackResponse findToday() {
        return snapMemoryService.findFlashbacks(LocalDate.now());
    }

    @GetMapping
    public FlashbackResponse findByDate(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return snapMemoryService.findFlashbacks(date);
    }
}