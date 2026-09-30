package com.example.retirement_planner;

import java.math.BigDecimal;

/** Rates are effective annual rates in decimal form (0.05 means 5%). */
public record ProjectionRequest(
        Integer currentAge,
        Integer retirementAge,
        BigDecimal currentSavings,
        BigDecimal monthlyContribution,
        BigDecimal annualReturnRate,
        BigDecimal annualInflationRate) {
}
