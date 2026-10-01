package com.example.retirement_planner;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/plans")
public class PlanningController {
    private final RetirementPlanService retirement;
    private final AccountRulesService accounts;
    private final GoalsService goals;
    private final JsonMapper mapper;
    public PlanningController(RetirementPlanService retirement, AccountRulesService accounts, GoalsService goals, JsonMapper mapper) {
        this.retirement = retirement; this.accounts = accounts; this.goals = goals; this.mapper = mapper;
    }
    @PostMapping("/retirement")
    public RetirementPlanResult retirement(@RequestBody RetirementPlanRequest request) { return retirement.project(request); }
    @PostMapping(value = "/retirement/csv", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> retirementCsv(@RequestParam("plan") String json) {
        RetirementPlanRequest request;
        try { request = mapper.readValue(json, RetirementPlanRequest.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Send a valid retirement plan with whole-number ages and numeric amounts."); }
        var result = retirement.project(request);
        var csv = new StringBuilder("Age,Phase,Contributions,Income,Withdrawals,Growth,Balance,Today dollars,Unfunded\r\n");
        for (var year : result.savings().annualBreakdown()) csv.append(year.ageAtYearEnd()).append(",Saving,")
                .append(year.contributions().toPlainString()).append(",0.00,0.00,").append(year.investmentGrowth().toPlainString()).append(',')
                .append(year.endingBalance().toPlainString()).append(',').append(year.inflationAdjustedEndingBalance().toPlainString()).append(",0.00\r\n");
        for (var year : result.retirementYears()) csv.append(year.ageAtYearEnd()).append(",Retirement,0.00,")
                .append(year.pensionIncome().toPlainString()).append(',').append(year.withdrawals().toPlainString()).append(',')
                .append(year.investmentGrowth().toPlainString()).append(',').append(year.endingBalance().toPlainString()).append(',')
                .append(year.inflationAdjustedEndingBalance().toPlainString()).append(',').append(year.unfundedSpending().toPlainString()).append("\r\n");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dream-planner-annual-plan.csv\"").body(csv.toString());
    }
    @PostMapping("/accounts")
    public AccountRulesResult accounts(@RequestBody AccountRulesRequest request) { return accounts.assess(request); }
    @PostMapping("/home")
    public GoalsService.HomeResult home(@RequestBody GoalsService.HomeRequest request) { return goals.home(request); }
    @PostMapping("/budget")
    public GoalsService.BudgetResult budget(@RequestBody GoalsService.BudgetRequest request) { return goals.budget(request); }
    @PostMapping(value = "/export", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> exportPlan(@RequestParam("plan") String json) {
        if (json.length() > 100000) throw new IllegalArgumentException("Plan files must be smaller than 100 KB.");
        java.util.Map<?, ?> envelope;
        try { envelope = mapper.readValue(json, java.util.Map.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Send a complete Dream Planner 2026 plan file."); }
        if (envelope == null || !Integer.valueOf(1).equals(envelope.get("version"))
                || !AccountRulesService.AS_OF.toString().equals(envelope.get("assessmentDate"))
                || !(envelope.get("plan") instanceof java.util.Map))
            throw new IllegalArgumentException("Send a complete Dream Planner 2026 plan file.");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dream-planner-plan.json\"")
                .body(mapper.writeValueAsString(envelope));
    }
}
