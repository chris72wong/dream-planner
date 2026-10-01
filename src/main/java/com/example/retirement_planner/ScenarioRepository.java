package com.example.retirement_planner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ScenarioRepository {
    public record SaveRequest(String name, Map<String, Object> document) { }
    public record SavedScenario(String id, String name, Map<?, ?> document, String createdAt) { }
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private final PlanDocumentValidator validator;
    public ScenarioRepository(JdbcTemplate jdbc, JsonMapper mapper, PlanDocumentValidator validator) { this.jdbc = jdbc; this.mapper = mapper; this.validator = validator; }
    public List<SavedScenario> list() {
        return jdbc.query("SELECT id, name, plan_json, created_at FROM saved_scenarios ORDER BY created_at DESC",
                (row, index) -> new SavedScenario(row.getString(1), row.getString(2), mapper.readValue(row.getString(3), Map.class), row.getString(4)));
    }
    public synchronized SavedScenario save(SaveRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.name().length() > 80)
            throw new IllegalArgumentException("Give your scenario a name of 1–80 characters.");
        var document = request.document();
        if (document == null || !Integer.valueOf(1).equals(document.get("version"))
                || !AccountRulesService.AS_OF.toString().equals(document.get("assessmentDate"))
                || !(document.get("plan") instanceof Map<?, ?> plan) || plan.size() < 20)
            throw new IllegalArgumentException("Send a complete Dream Planner 2026 plan document.");
        var json = mapper.writeValueAsString(document);
        if (json.length() > 100000) throw new IllegalArgumentException("Plan documents must be smaller than 100 KB.");
        validator.validate(document);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM saved_scenarios", Integer.class) >= 100)
            throw new IllegalArgumentException("Your library holds up to 100 plans. Remove a plan before adding another.");
        var id = UUID.randomUUID().toString(); var now = Instant.now().toString();
        jdbc.update("INSERT INTO saved_scenarios(id,name,plan_json,created_at) VALUES(?,?,?,?)", id, request.name().trim(), json, now);
        return new SavedScenario(id, request.name().trim(), document, now);
    }
    public boolean delete(String id) {
        try { UUID.fromString(id); } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid scenario id."); }
        return jdbc.update("DELETE FROM saved_scenarios WHERE id = ?", id) == 1;
    }
}
