package com.example.retirement_planner;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projections")
public class ProjectionController {
    private final ProjectionService service;

    public ProjectionController(ProjectionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProjectionResult project(@RequestBody ProjectionRequest request) {
        return service.project(request);
    }

    @PostMapping(path = "/csv", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = "text/csv")
    public ResponseEntity<String> export(@ModelAttribute ProjectionRequest request) {
        var result = service.project(request);
        var csv = new StringBuilder("Projection year,Age at year-end,Contributions (CAD),Investment growth (CAD),Ending balance (CAD),Ending balance in today's dollars (CAD)\r\n");
        for (var row : result.annualBreakdown()) {
            csv.append(row.projectionYear()).append(',').append(row.ageAtYearEnd()).append(',')
                    .append(row.contributions().toPlainString()).append(',')
                    .append(row.investmentGrowth().toPlainString()).append(',')
                    .append(row.endingBalance().toPlainString()).append(',')
                    .append(row.inflationAdjustedEndingBalance().toPlainString()).append("\r\n");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"retirement-projection.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString());
    }
}
