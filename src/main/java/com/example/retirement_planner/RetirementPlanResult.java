package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.List;

public record RetirementPlanResult(ProjectionResult savings, BigDecimal requiredAtRetirement,
        BigDecimal fundingGap, BigDecimal requiredMonthlyContribution, BigDecimal sustainableMonthlySpending,
        BigDecimal endingBalance, BigDecimal totalUnfundedSpending, Integer firstShortfallAge,
        Integer firstShortfallMonth, BigDecimal adjustedCppMonthly, BigDecimal adjustedOasMonthly,
        List<RetirementYear> retirementYears) {
    public RetirementPlanResult { retirementYears = List.copyOf(retirementYears); }
    public record RetirementYear(int ageAtYearEnd, BigDecimal spending, BigDecimal pensionIncome,
            BigDecimal withdrawals, BigDecimal investmentGrowth, BigDecimal unfundedSpending,
            BigDecimal endingBalance, BigDecimal inflationAdjustedEndingBalance) { }
}
