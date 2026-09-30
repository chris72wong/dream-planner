package com.example.retirement_planner;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final ScenarioRepository repository;
    public ScenarioController(ScenarioRepository repository) { this.repository = repository; }
    @GetMapping public List<ScenarioRepository.SavedScenario> list() { return repository.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ScenarioRepository.SavedScenario save(@RequestBody ScenarioRepository.SaveRequest request) { return repository.save(request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!repository.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found.");
    }
}
