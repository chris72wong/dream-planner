package com.example.retirement_planner;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import static com.example.retirement_planner.PlanMath.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    public record HouseholdRequest(RetirementPlanRequest primary, RetirementPlanRequest partner) { }
    public record HouseholdResult(RetirementPlanResult primary, RetirementPlanResult partner,
                                  BigDecimal combinedSupportedMonthlySpending, String assumptions) { }
    private final RiskService risk;
    private final DrawdownService drawdown;
    private final RetirementPlanService retirement;
    public AnalysisController(RiskService risk, DrawdownService drawdown, RetirementPlanService retirement) { this.risk=risk; this.drawdown=drawdown; this.retirement=retirement; }
    @PostMapping("/risk") public RiskService.Result risk(@RequestBody RiskService.Request request) { return risk.simulate(request); }
    @PostMapping("/drawdown") public DrawdownService.Result drawdown(@RequestBody DrawdownService.Request request) { return drawdown.project(request); }
    @PostMapping("/household") public HouseholdResult household(@RequestBody HouseholdRequest request) {
        require(request,"household"); var primary=retirement.project(request.primary()); var partner=retirement.project(request.partner());
        if (request.primary().savings().annualInflationRate().compareTo(request.partner().savings().annualInflationRate()) != 0)
            throw new IllegalArgumentException("Use the same inflation assumption for both household members.");
        return new HouseholdResult(primary,partner,primary.sustainableMonthlySpending().add(partner.sustainableMonthlySpending()),
                "Two independent before-tax plans, summed in today's dollars. Each member's own retirement and planning ages apply. No asset sharing, pension splitting, survivor benefits or coordinated tax optimisation.");
    }
}
