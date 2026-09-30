package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import static com.example.retirement_planner.PlanMath.*;

@Service
public class RetirementPlanService {
    private final ProjectionService projections;
    public RetirementPlanService(ProjectionService projections) { this.projections = projections; }

    public RetirementPlanResult project(RetirementPlanRequest request) {
        require(request, "plan");
        require(request.savings(), "savings");
        var saving = projections.project(request.savings());
        var inputs = request.savings();
        range(request.planningAge(), inputs.retirementAge() + 1, 120, "planningAge");
        amount(request.monthlySpending(), "monthlySpending");
        amount(request.cppMonthlyAt65(), "cppMonthlyAt65");
        amount(request.oasMonthlyAt65(), "oasMonthlyAt65");
        amount(request.otherMonthlyIncome(), "otherMonthlyIncome");
        range(request.cppStartAge(), 60, 70, "cppStartAge");
        range(request.oasStartAge(), 65, 70, "oasStartAge");
        range(request.otherIncomeStartAge(), 0, 120, "otherIncomeStartAge");
        var growthFactor = factor(request.retirementReturnRate(), "retirementReturnRate");
        var inflationFactor = factor(inputs.annualInflationRate(), "annualInflationRate");
        var savingsFactor = factor(inputs.annualReturnRate(), "annualReturnRate");
        int savingsMonths = (inputs.retirementAge() - inputs.currentAge()) * 12;
        int retirementMonths = (request.planningAge() - inputs.retirementAge()) * 12;
        var cpp = request.cppMonthlyAt65().multiply(BigDecimal.valueOf(request.cppStartAge() < 65
                ? 1 - (65 - request.cppStartAge()) * .072 : 1 + (request.cppStartAge() - 65) * .084), MC);
        var oas = request.oasMonthlyAt65().multiply(BigDecimal.valueOf(1 + (request.oasStartAge() - 65) * .072), MC);
        var inflation = inflationFactor.pow(savingsMonths, MC);
        BigDecimal[] spendingFactors = new BigDecimal[retirementMonths];
        BigDecimal[] incomes = new BigDecimal[retirementMonths];
        BigDecimal[] discounts = new BigDecimal[retirementMonths];
        var discount = ONE;
        for (int m = 0; m < retirementMonths; m++) {
            inflation = inflation.multiply(inflationFactor, MC);
            discount = discount.divide(growthFactor, MC);
            int age = inputs.retirementAge() + m / 12;
            var monthlyIncome = ZERO;
            if (age >= request.cppStartAge()) monthlyIncome = monthlyIncome.add(cpp);
            if (age >= request.oasStartAge()) monthlyIncome = monthlyIncome.add(age >= 75 ? oas.multiply(new BigDecimal("1.10")) : oas);
            if (age >= request.otherIncomeStartAge()) monthlyIncome = monthlyIncome.add(request.otherMonthlyIncome());
            spendingFactors[m] = inflation;
            incomes[m] = monthlyIncome.multiply(inflation, MC);
            discounts[m] = discount;
        }
        var target = required(request.monthlySpending(), spendingFactors, incomes, discounts);
        var unitContribution = ZERO;
        for (int m = 0; m < savingsMonths; m++) unitContribution = unitContribution.multiply(savingsFactor, MC).add(ONE);
        var futureInitial = inputs.currentSavings().multiply(savingsFactor.pow(savingsMonths, MC), MC);
        var monthlyRequired = target.subtract(futureInitial).max(ZERO).divide(unitContribution, MC);
        // Monotonic spending solver: pension surplus is consumed, never silently saved.
        var low = ZERO;
        var high = new BigDecimal("1000000000");
        for (int i = 0; i < 48; i++) {
            var mid = low.add(high).divide(BigDecimal.TWO, MC);
            if (required(mid, spendingFactors, incomes, discounts).compareTo(saving.finalBalance()) <= 0) low = mid;
            else high = mid;
        }
        var balance = saving.finalBalance();
        var unfundedTotal = ZERO;
        Integer shortfallAge = null, shortfallMonth = null;
        var years = new ArrayList<RetirementPlanResult.RetirementYear>();
        var yearSpend = ZERO; var yearIncome = ZERO; var yearWithdrawal = ZERO; var yearGrowth = ZERO; var yearUnfunded = ZERO;
        for (int m = 0; m < retirementMonths; m++) {
            var growth = balance.multiply(growthFactor.subtract(ONE), MC);
            balance = balance.add(growth, MC);
            var spend = request.monthlySpending().multiply(spendingFactors[m], MC);
            var need = spend.subtract(incomes[m]).max(ZERO);
            var withdrawal = need.min(balance);
            var shortfall = need.subtract(withdrawal);
            balance = balance.subtract(withdrawal);
            if (shortfall.compareTo(new BigDecimal("0.005")) >= 0 && shortfallAge == null) {
                shortfallAge = inputs.retirementAge() + m / 12; shortfallMonth = m % 12 + 1;
            }
            yearSpend = yearSpend.add(spend); yearIncome = yearIncome.add(incomes[m]);
            yearWithdrawal = yearWithdrawal.add(withdrawal); yearGrowth = yearGrowth.add(growth); yearUnfunded = yearUnfunded.add(shortfall);
            unfundedTotal = unfundedTotal.add(shortfall);
            if ((m + 1) % 12 == 0) {
                years.add(new RetirementPlanResult.RetirementYear(inputs.retirementAge() + (m + 1) / 12,
                        money(yearSpend), money(yearIncome), money(yearWithdrawal), money(yearGrowth), money(yearUnfunded),
                        money(balance), money(balance.divide(spendingFactors[m], MC))));
                yearSpend = ZERO; yearIncome = ZERO; yearWithdrawal = ZERO; yearGrowth = ZERO; yearUnfunded = ZERO;
            }
        }
        return new RetirementPlanResult(saving, ceilMoney(target), money(target.subtract(saving.finalBalance())),
                ceilMoney(monthlyRequired), low.setScale(2, java.math.RoundingMode.FLOOR), money(balance), money(unfundedTotal),
                shortfallAge, shortfallMonth, money(cpp), money(oas), years);
    }

    private BigDecimal required(BigDecimal spend, BigDecimal[] factors, BigDecimal[] income, BigDecimal[] discounts) {
        var sum = ZERO;
        for (int i = 0; i < factors.length; i++)
            sum = sum.add(spend.multiply(factors[i], MC).subtract(income[i]).max(ZERO).multiply(discounts[i], MC), MC);
        return sum;
    }
}
