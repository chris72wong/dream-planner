package com.example.retirement_planner;

import java.math.BigDecimal;

/** Projection year is one-based, relative to the start of the projection. */
public record AnnualProjection(
        int projectionYear,
        int ageAtYearEnd,
        BigDecimal contributions,
        BigDecimal investmentGrowth,
        BigDecimal endingBalance,
        BigDecimal inflationAdjustedEndingBalance) {
}
