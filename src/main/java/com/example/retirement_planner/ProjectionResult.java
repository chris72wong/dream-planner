package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.List;

/** All reported monetary amounts are rounded independently to two decimal places. */
public record ProjectionResult(
        BigDecimal initialSavings,
        BigDecimal totalContributions,
        BigDecimal totalInvestmentGrowth,
        BigDecimal finalBalance,
        BigDecimal inflationAdjustedFinalBalance,
        List<AnnualProjection> annualBreakdown) {

    public ProjectionResult {
        annualBreakdown = List.copyOf(annualBreakdown);
    }
}
