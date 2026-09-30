package com.example.retirement_planner;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

/** Deterministic savings projection; excludes taxes, fees, withdrawals and account rules. */
@Service
public class ProjectionService {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public ProjectionResult project(ProjectionRequest request) {
        validate(request);
        int years = request.retirementAge() - request.currentAge();
        BigDecimal monthlyRate = monthlyRate(request.annualReturnRate());
        BigDecimal annualInflationFactor = BigDecimal.ONE.add(request.annualInflationRate());
        BigDecimal cumulativeInflation = BigDecimal.ONE;
        BigDecimal balance = request.currentSavings();
        BigDecimal yearlyContributions = request.monthlyContribution().multiply(TWELVE);
        var annualBreakdown = new ArrayList<AnnualProjection>(years);
        BigDecimal realBalance = balance;

        for (int year = 1; year <= years; year++) {
            BigDecimal startingBalance = balance;
            for (int month = 0; month < 12; month++) {
                BigDecimal growth = balance.multiply(monthlyRate, PRECISION);
                balance = balance.add(growth, PRECISION)
                        .add(request.monthlyContribution(), PRECISION);
            }
            cumulativeInflation = cumulativeInflation.multiply(annualInflationFactor, PRECISION);
            realBalance = balance.divide(cumulativeInflation, PRECISION);
            BigDecimal yearlyGrowth = balance.subtract(startingBalance).subtract(yearlyContributions);
            annualBreakdown.add(new AnnualProjection(year, request.currentAge() + year,
                    money(yearlyContributions), money(yearlyGrowth), money(balance), money(realBalance)));
        }

        BigDecimal totalContributions = yearlyContributions.multiply(BigDecimal.valueOf(years));
        BigDecimal totalGrowth = balance.subtract(request.currentSavings()).subtract(totalContributions);
        return new ProjectionResult(money(request.currentSavings()), money(totalContributions),
                money(totalGrowth), money(balance), money(realBalance), annualBreakdown);
    }

    /**
     * The fractional power uses StrictMath/double because BigDecimal has no fractional pow.
     * log1p/expm1 evaluate (1 + annualRate)^(1/12) - 1 accurately near zero.
     * Only this rate conversion uses floating point; monetary calculations use DECIMAL128
     * (34 significant digits), never rounding the running balance to cents.
     */
    private static BigDecimal monthlyRate(BigDecimal annualRate) {
        // Add in decimal first so a valid rate extremely close to -1 stays positive.
        BigDecimal annualFactor = BigDecimal.ONE.add(annualRate);
        double rate = annualRate.doubleValue();
        double logarithm = rate > -1.0
                ? StrictMath.log1p(rate)
                : StrictMath.log(annualFactor.doubleValue());
        return BigDecimal.valueOf(StrictMath.expm1(logarithm / 12.0));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validate(ProjectionRequest request) {
        require(request, "request");
        require(request.currentAge(), "currentAge");
        require(request.retirementAge(), "retirementAge");
        require(request.currentSavings(), "currentSavings");
        require(request.monthlyContribution(), "monthlyContribution");
        require(request.annualReturnRate(), "annualReturnRate");
        require(request.annualInflationRate(), "annualInflationRate");
        if (request.currentAge() < 0 || request.currentAge() >= request.retirementAge()
                || request.retirementAge() > 120) {
            throw new IllegalArgumentException("ages must satisfy 0 <= currentAge < retirementAge <= 120");
        }
        if (request.currentSavings().signum() < 0) {
            throw new IllegalArgumentException("currentSavings must be non-negative");
        }
        if (request.monthlyContribution().signum() < 0) {
            throw new IllegalArgumentException("monthlyContribution must be non-negative");
        }
        validateRate(request.annualReturnRate(), "annualReturnRate");
        validateRate(request.annualInflationRate(), "annualInflationRate");
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void validateRate(BigDecimal value, String name) {
        if (value.compareTo(BigDecimal.ONE.negate()) <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " must be greater than -1 and no greater than 1");
        }
    }
}
