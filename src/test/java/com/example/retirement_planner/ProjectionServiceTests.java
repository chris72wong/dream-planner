package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionServiceTests {
    private final ProjectionService service = new ProjectionService();

    @Test
    void projectsExactly420MonthsWithZeroRates() {
        var result = service.project(request(30, 65, "10000", "500", "0", "0"));
        assertEquals(money("220000"), result.finalBalance());
        assertEquals(money("220000"), result.inflationAdjustedFinalBalance());
        assertEquals(money("10000"), result.initialSavings());
        assertEquals(money("210000"), result.totalContributions());
        assertEquals(money("0"), result.totalInvestmentGrowth());
        assertEquals(35, result.annualBreakdown().size());
        for (int i = 0; i < 35; i++) {
            var annual = result.annualBreakdown().get(i);
            assertEquals(i + 1, annual.projectionYear());
            assertEquals(31 + i, annual.ageAtYearEnd());
            assertEquals(money("6000"), annual.contributions());
            assertEquals(money("0"), annual.investmentGrowth());
            assertEquals(money(Integer.toString(10000 + (i + 1) * 6000)), annual.endingBalance());
            assertEquals(annual.endingBalance(), annual.inflationAdjustedEndingBalance());
        }
    }

    @Test
    void compoundsInitialSavingsWithoutContributions() {
        var result = service.project(request(60, 62, "10000", "0", "0.05", "0"));
        assertEquals(money("11025"), result.finalBalance());
        assertEquals(money("0"), result.totalContributions());
        assertEquals(money("1025"), result.totalInvestmentGrowth());
        assertEquals(money("10500"), result.annualBreakdown().getFirst().endingBalance());
    }

    @Test
    void usesEffectiveMonthlyCompoundingAndEndOfMonthContributions() {
        // This effective annual rate is exactly 1.01^12 - 1: a 1% monthly return.
        var result = service.project(request(30, 31, "1000", "100", "0.126825030131969720661201", "0"));
        // Independently derived annuity: 1000 * 1.01^12 + 100 * (1.01^12 - 1) / 0.01.
        assertEquals(money("2395.08"), result.finalBalance());
        assertEquals(money("195.08"), result.totalInvestmentGrowth());
        var contributionsOnly = service.project(request(30, 31, "0", "100", "0.126825030131969720661201", "0"));
        assertEquals(money("1268.25"), contributionsOnly.finalBalance());
    }

    @Test
    void doesNotRoundEachMonthlyBalanceToCents() {
        // Rounding each month's 0.4-cent interest would incorrectly leave the balance at $0.40.
        var result = service.project(request(30, 31, "0.40", "0", "0.126825030131969720661201", "0"));
        assertEquals(money("0.45"), result.finalBalance());
    }

    @Test
    void adjustsAnnualBalancesForCumulativeInflationWhileContributionsStayFixed() {
        var result = service.project(request(60, 62, "10000", "100", "0", "0.10"));
        assertEquals(money("11200"), result.annualBreakdown().getFirst().endingBalance());
        assertEquals(money("10181.82"), result.annualBreakdown().getFirst().inflationAdjustedEndingBalance());
        assertEquals(money("12400"), result.finalBalance());
        assertEquals(money("10247.93"), result.inflationAdjustedFinalBalance());
        assertEquals(money("1200"), result.annualBreakdown().getLast().contributions());
    }

    @Test
    void supportsNegativeReturn() {
        var result = service.project(request(60, 62, "10000", "0", "-0.12", "0"));
        assertEquals(money("8800"), result.annualBreakdown().getFirst().endingBalance());
        assertEquals(money("7744"), result.finalBalance());
        assertEquals(money("-2256"), result.totalInvestmentGrowth());
    }

    @Test
    void reportsAnnualGrowthAndTotals() {
        var result = service.project(request(30, 32, "1000", "100", "0.126825030131969720661201", "0"));
        assertEquals(money("3967.08"), result.finalBalance());
        assertEquals(money("2400"), result.totalContributions());
        assertEquals(money("567.08"), result.totalInvestmentGrowth());
        assertEquals(money("195.08"), result.annualBreakdown().getFirst().investmentGrowth());
        // Unrounded year-two growth is 372.0058...; rounded annual growth sums to 567.09,
        // while growth across both years rounds independently to 567.08.
        assertEquals(money("372.01"), result.annualBreakdown().getLast().investmentGrowth());
        assertEquals(result.finalBalance(), result.annualBreakdown().getLast().endingBalance());
        assertEquals(result.inflationAdjustedFinalBalance(), result.annualBreakdown().getLast().inflationAdjustedEndingBalance());
        assertEquals(result.finalBalance(), result.initialSavings()
                .add(result.totalContributions()).add(result.totalInvestmentGrowth()));
        assertThrows(UnsupportedOperationException.class, () -> result.annualBreakdown().clear());
    }

    @Test
    void acceptsBoundaryAgesAndMaximumRates() {
        var result = service.project(request(0, 120, "1", "0", "1", "1"));
        assertEquals(120, result.annualBreakdown().size());
        assertEquals(120, result.annualBreakdown().getLast().ageAtYearEnd());
        assertEquals(money("1"), result.inflationAdjustedFinalBalance());
    }

    @Test
    void supportsDeflationAndRatesCloseToMinusOne() {
        var result = service.project(request(30, 31, "100", "0", "0", "-0.5"));
        assertEquals(money("200"), result.inflationAdjustedFinalBalance());
        assertEquals(money("0"), service.project(request(30, 31, "100", "0", "-0.999999999999999999", "0")).finalBalance());
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidInputsWithClearErrors(ProjectionRequest request, String message) {
        var error = assertThrows(IllegalArgumentException.class, () -> service.project(request));
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, "request is required"),
                Arguments.of(new ProjectionRequest(null, 65, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), "currentAge is required"),
                Arguments.of(new ProjectionRequest(30, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), "retirementAge is required"),
                Arguments.of(request(30, 65, null, "0", "0", "0"), "currentSavings is required"),
                Arguments.of(request(30, 65, "0", null, "0", "0"), "monthlyContribution is required"),
                Arguments.of(request(30, 65, "0", "0", null, "0"), "annualReturnRate is required"),
                Arguments.of(request(30, 65, "0", "0", "0", null), "annualInflationRate is required"),
                Arguments.of(request(-1, 65, "0", "0", "0", "0"), "ages must satisfy"),
                Arguments.of(request(65, 65, "0", "0", "0", "0"), "ages must satisfy"),
                Arguments.of(request(66, 65, "0", "0", "0", "0"), "ages must satisfy"),
                Arguments.of(request(30, 121, "0", "0", "0", "0"), "ages must satisfy"),
                Arguments.of(request(30, 65, "-0.01", "0", "0", "0"), "currentSavings must be non-negative"),
                Arguments.of(request(30, 65, "0", "-0.01", "0", "0"), "monthlyContribution must be non-negative"),
                Arguments.of(request(30, 65, "0", "0", "-1", "0"), "annualReturnRate must be greater than -1"),
                Arguments.of(request(30, 65, "0", "0", "-1.01", "0"), "annualReturnRate must be greater than -1"),
                Arguments.of(request(30, 65, "0", "0", "1.01", "0"), "annualReturnRate must be greater than -1"),
                Arguments.of(request(30, 65, "0", "0", "0", "-1"), "annualInflationRate must be greater than -1"),
                Arguments.of(request(30, 65, "0", "0", "0", "-1.01"), "annualInflationRate must be greater than -1"),
                Arguments.of(request(30, 65, "0", "0", "0", "1.01"), "annualInflationRate must be greater than -1"));
    }

    private static ProjectionRequest request(int currentAge, int retirementAge, String savings,
                                             String contribution, String annualReturn, String inflation) {
        return new ProjectionRequest(currentAge, retirementAge, decimal(savings), decimal(contribution),
                decimal(annualReturn), decimal(inflation));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
