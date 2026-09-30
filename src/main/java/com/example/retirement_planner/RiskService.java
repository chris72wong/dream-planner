package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Random;
import org.springframework.stereotype.Service;
import static com.example.retirement_planner.PlanMath.*;

@Service
public class RiskService {
    public record Request(RetirementPlanRequest plan, BigDecimal volatility, Integer simulations, Long seed) { }
    public record Result(int simulations, long seed, BigDecimal successPercent, BigDecimal retirementP10,
                         BigDecimal retirementP50, BigDecimal retirementP90, BigDecimal endingP10,
                         BigDecimal endingP50, BigDecimal endingP90, String assumptions) { }
    private final RetirementPlanService retirement;
    public RiskService(RetirementPlanService retirement) { this.retirement = retirement; }
    public Result simulate(Request request) {
        require(request, "simulation"); retirement.project(request.plan());
        amount(request.volatility(), "volatility");
        if (request.volatility().compareTo(new BigDecimal("0.6")) > 0) throw new IllegalArgumentException("Volatility must be at most 60%.");
        range(request.simulations(), 100, 2000, "simulations"); require(request.seed(), "seed");
        var p = request.plan(); var s = p.savings();
        double finalInflation=StrictMath.pow(1+s.annualInflationRate().doubleValue(),p.planningAge()-s.currentAge());
        if(!Double.isFinite(finalInflation)||finalInflation<1e-200)
            throw new IllegalArgumentException("The inflation assumption is too extreme for this simulation horizon.");
        var random = new Random(request.seed()); int successes = 0;
        double[] atRetirement = new double[request.simulations()], endings = new double[request.simulations()];
        double sigma = request.volatility().doubleValue(), inflationStep = factor(s.annualInflationRate(), "inflation").doubleValue();
        int savingMonths = (s.retirementAge() - s.currentAge()) * 12;
        int totalMonths = (p.planningAge() - s.currentAge()) * 12;
        double cpp = p.cppMonthlyAt65().doubleValue() * (p.cppStartAge() < 65 ? 1 - (65 - p.cppStartAge()) * .072 : 1 + (p.cppStartAge() - 65) * .084);
        double oas = p.oasMonthlyAt65().doubleValue() * (1 + (p.oasStartAge() - 65) * .072);
        for (int run = 0; run < request.simulations(); run++) {
            double balance = s.currentSavings().doubleValue(), inflation = 1, growth = 1; boolean success = true;
            for (int month = 0; month < totalMonths; month++) {
                boolean saving = month < savingMonths;
                if (month % 12 == 0) {
                    double mean = saving ? s.annualReturnRate().doubleValue() : p.retirementReturnRate().doubleValue();
                    // A lognormal gross-return process, with an arithmetic expected return of the entered mean.
                    growth = StrictMath.exp((StrictMath.log1p(mean) - sigma * sigma / 2 + sigma * random.nextGaussian()) / 12);
                }
                balance *= growth; inflation *= inflationStep;
                if (saving) balance += s.monthlyContribution().doubleValue();
                else {
                    int age = s.currentAge() + month / 12;
                    double income = (age >= p.cppStartAge() ? cpp : 0) + (age >= p.oasStartAge() ? oas * (age >= 75 ? 1.1 : 1) : 0)
                            + (age >= p.otherIncomeStartAge() ? p.otherMonthlyIncome().doubleValue() : 0);
                    double need = Math.max(0, (p.monthlySpending().doubleValue() - income) * inflation);
                    if (need > balance + .00001) success = false;
                    balance = Math.max(0, balance - need);
                }
                if (month == savingMonths - 1) atRetirement[run] = balance / inflation;
            }
            endings[run] = balance / inflation; if (success) successes++;
        }
        Arrays.sort(atRetirement); Arrays.sort(endings);
        return new Result(request.simulations(), request.seed(), money(BigDecimal.valueOf(100.0 * successes / request.simulations())),
                percentile(atRetirement,.1), percentile(atRetirement,.5), percentile(atRetirement,.9),
                percentile(endings,.1), percentile(endings,.5), percentile(endings,.9),
                "Before tax; independent lognormal annual returns, constant within each year; fixed inflation; no fees. Percentiles in today's CAD. Illustrative model frequency, not a forecast.");
    }
    private static BigDecimal percentile(double[] values, double quantile) {
        double value=values[(int)Math.floor((values.length-1)*quantile)];
        if(!Double.isFinite(value)) throw new IllegalArgumentException("The assumptions produce values too large for risk analysis.");
        return money(BigDecimal.valueOf(value));
    }
}
