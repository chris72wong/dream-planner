package com.example.retirement_planner;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import static com.example.retirement_planner.PlanMath.*;

@Service
public class GoalsService {
    private final ProjectionService projections;
    public GoalsService(ProjectionService projections) { this.projections = projections; }
    public record HomeRequest(BigDecimal currentSavings, BigDecimal monthlyContribution, BigDecimal targetToday,
            Integer years, BigDecimal annualReturnRate, BigDecimal annualInflationRate) { }
    public record HomeResult(ProjectionResult projection, BigDecimal nominalTarget, BigDecimal requiredMonthlyContribution) { }
    public HomeResult home(HomeRequest r) {
        require(r, "home goal"); range(r.years(), 1, 30, "years");
        amount(r.currentSavings(), "currentSavings"); amount(r.monthlyContribution(), "monthlyContribution"); amount(r.targetToday(), "targetToday");
        var growth = factor(r.annualReturnRate(), "annualReturnRate");
        var inflation = factor(r.annualInflationRate(), "annualInflationRate");
        var target = r.targetToday().multiply(inflation.pow(r.years() * 12, MC), MC);
        var unit = ZERO;
        for (int m = 0; m < r.years() * 12; m++) unit = unit.multiply(growth, MC).add(ONE);
        var needed = target.subtract(r.currentSavings().multiply(growth.pow(r.years() * 12, MC), MC)).max(ZERO).divide(unit, MC);
        var projection = projections.project(new ProjectionRequest(0, r.years(), r.currentSavings(), r.monthlyContribution(), r.annualReturnRate(), r.annualInflationRate()));
        return new HomeResult(projection, money(target), ceilMoney(needed));
    }
    public record BudgetRequest(BigDecimal monthlyTakeHome, BigDecimal monthlyExpenses, BigDecimal retirementSaving,
            BigDecimal homeSaving, BigDecimal emergencyCash, Integer emergencyMonths, BigDecimal debtBalance,
            BigDecimal debtApr, BigDecimal debtPayment) { }
    public record BudgetResult(BigDecimal monthlyUnallocated, BigDecimal emergencyTarget, BigDecimal emergencyGap,
            BigDecimal emergencyMonthsCovered, Integer debtPayoffMonths, BigDecimal debtInterest,
            boolean debtPaymentTooLow, boolean exceedsFiftyYears) { }
    public BudgetResult budget(BudgetRequest r) {
        require(r, "budget");
        amount(r.monthlyTakeHome(), "monthlyTakeHome"); amount(r.monthlyExpenses(), "monthlyExpenses");
        amount(r.retirementSaving(), "retirementSaving"); amount(r.homeSaving(), "homeSaving"); amount(r.emergencyCash(), "emergencyCash");
        range(r.emergencyMonths(), 1, 24, "emergencyMonths"); amount(r.debtBalance(), "debtBalance"); amount(r.debtPayment(), "debtPayment");
        require(r.debtApr(), "debtApr");
        if (r.debtApr().signum() < 0 || r.debtApr().compareTo(ONE) > 0) throw new IllegalArgumentException("debtApr must be between 0 and 1");
        var rate = r.debtApr().divide(BigDecimal.valueOf(12), MC);
        var balance = r.debtBalance(); var interest = ZERO; int months = 0;
        boolean tooLow = balance.signum() > 0 && (r.debtPayment().signum() == 0 || r.debtPayment().compareTo(balance.multiply(rate, MC)) <= 0);
        if (!tooLow) while (balance.signum() > 0 && months < 600) {
            var cost = balance.multiply(rate, MC);
            interest = interest.add(cost); balance = balance.add(cost).subtract(r.debtPayment()).max(ZERO); months++;
        }
        boolean longPayoff = !tooLow && balance.signum() > 0;
        var target = r.monthlyExpenses().multiply(BigDecimal.valueOf(r.emergencyMonths()));
        return new BudgetResult(money(r.monthlyTakeHome().subtract(r.monthlyExpenses()).subtract(r.retirementSaving()).subtract(r.homeSaving()).subtract(r.debtPayment())),
                money(target), money(target.subtract(r.emergencyCash()).max(ZERO)),
                r.monthlyExpenses().signum() == 0 ? null : money(r.emergencyCash().divide(r.monthlyExpenses(), MC)),
                tooLow || longPayoff ? null : months, tooLow || longPayoff ? null : money(interest), tooLow, longPayoff);
    }
}
