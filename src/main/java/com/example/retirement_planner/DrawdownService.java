package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import static com.example.retirement_planner.PlanMath.*;

@Service
public class DrawdownService {
    public record Request(RetirementPlanRequest plan, String province, BigDecimal tfsa, BigDecimal rrsp,
                          BigDecimal other, BigDecimal rrspContributionShare, BigDecimal tfsaContributionShare, String strategy) { }
    public record Year(int age, BigDecimal spending, BigDecimal pension, BigDecimal tfsaWithdrawal,
                       BigDecimal rrspWithdrawal, BigDecimal otherWithdrawal, BigDecimal rrifMinimum,
                       BigDecimal incomeTax, BigDecimal oasRecovery, BigDecimal shortfall,
                       BigDecimal tfsa, BigDecimal rrsp, BigDecimal other) { }
    public record Result(List<Year> years, BigDecimal totalTax, BigDecimal totalOasRecovery,
                         BigDecimal totalShortfall, BigDecimal endingBalance, String assumptions) { }
    private final RetirementPlanService retirement;
    public DrawdownService(RetirementPlanService retirement) { this.retirement = retirement; }
    public Result project(Request request) {
        require(request, "drawdown"); var base = retirement.project(request.plan());
        if (!"ON".equals(request.province())) throw new IllegalArgumentException("The after-tax estimate currently supports Ontario only. The main before-tax plan supports every province.");
        amount(request.tfsa(), "tfsa"); amount(request.rrsp(), "rrsp"); amount(request.other(), "other");
        amount(request.tfsaContributionShare(), "tfsaContributionShare"); amount(request.rrspContributionShare(), "rrspContributionShare");
        double tfsaShare = request.tfsaContributionShare().doubleValue(), rrspShare = request.rrspContributionShare().doubleValue();
        if (tfsaShare + rrspShare > 1) throw new IllegalArgumentException("Contribution shares must total at most 100%.");
        if (!List.of("TFSA_FIRST", "RRSP_FIRST", "OTHER_FIRST").contains(request.strategy() == null ? "" : request.strategy()))
            throw new IllegalArgumentException("Choose a withdrawal strategy.");
        var p = request.plan(); var s = p.savings();
        double finalInflation=StrictMath.pow(1+s.annualInflationRate().doubleValue(),p.planningAge()-s.currentAge());
        if(!Double.isFinite(finalInflation)||finalInflation<1e-200)
            throw new IllegalArgumentException("The inflation assumption is too extreme for this tax-analysis horizon.");
        if (request.tfsa().add(request.rrsp()).add(request.other()).compareTo(s.currentSavings()) != 0)
            throw new IllegalArgumentException("Account balances must match current retirement savings.");
        double[] balances = {request.tfsa().doubleValue(), request.rrsp().doubleValue(), request.other().doubleValue()};
        double savingGrowth = factor(s.annualReturnRate(), "return").doubleValue();
        for (int m = 0; m < (s.retirementAge() - s.currentAge()) * 12; m++) {
            for (int i = 0; i < 3; i++) balances[i] *= savingGrowth;
            double contribution = s.monthlyContribution().doubleValue();
            balances[0] += contribution * tfsaShare;
            // RRSP contributions stop after the year of age 71; the remainder goes to the other account.
            double allocatedRrsp = s.currentAge() + m / 12 <= 71 ? contribution * rrspShare : 0;
            balances[1] += allocatedRrsp; balances[2] += contribution * (1 - tfsaShare) - allocatedRrsp;
        }
        double totalTax = 0, totalRecovery = 0, totalShortfall = 0;
        var years = new ArrayList<Year>();
        int[] order = switch(request.strategy()) { case "RRSP_FIRST" -> new int[]{1,2,0}; case "OTHER_FIRST" -> new int[]{2,1,0}; default -> new int[]{0,2,1}; };
        for (int y = 0; y < base.retirementYears().size(); y++) {
            var original = base.retirementYears().get(y); int age = s.retirementAge() + y;
            double minimum = age >= 72 ? balances[1] * rrifFactor(age) : 0;
            double otherGrowth = Math.max(0, balances[2] * p.retirementReturnRate().doubleValue());
            for (int i = 0; i < 3; i++) balances[i] *= 1 + p.retirementReturnRate().doubleValue();
            minimum = Math.min(balances[1], minimum);
            double cpi = StrictMath.pow(1 + s.annualInflationRate().doubleValue(), age - s.currentAge() + 1);
            double oas = 0;
            for (int m = 0; m < 12; m++) {
                double monthCpi = StrictMath.pow(1 + s.annualInflationRate().doubleValue(), age - s.currentAge() + (m + 1) / 12.0);
                if (age >= p.oasStartAge()) oas += base.adjustedOasMonthly().doubleValue() * (age >= 75 ? 1.1 : 1) * monthCpi;
            }
            double pension = original.pensionIncome().doubleValue(), spending = original.spending().doubleValue();
            double lo = 0, hi = balances[0] + balances[1] + balances[2];
            for (int iteration = 0; iteration < 60; iteration++) {
                double candidate = (lo + hi) / 2; var withdrawals = allocate(balances, minimum, candidate, order);
                double taxable = pension + withdrawals[1] + otherGrowth;
                double recovery = Math.min(oas, Math.max(0, taxable - 95323 * cpi) * .15);
                double tax = ontarioTax(Math.max(0, taxable - recovery) / cpi) * cpi;
                double net = pension + sum(withdrawals) - tax - recovery;
                if (net >= spending) hi = candidate; else lo = candidate;
            }
            var withdrawals = allocate(balances, minimum, hi, order);
            double taxable = pension + withdrawals[1] + otherGrowth;
            double recovery = Math.min(oas, Math.max(0, taxable - 95323 * cpi) * .15);
            double tax = ontarioTax(Math.max(0, taxable - recovery) / cpi) * cpi;
            double available = pension + sum(withdrawals) - tax - recovery;
            double shortfall = Math.max(0, spending - available);
            for (int i = 0; i < 3; i++) balances[i] = Math.max(0, balances[i] - withdrawals[i]);
            balances[2] += Math.max(0, available - spending);
            totalTax += tax; totalRecovery += recovery; totalShortfall += shortfall;
            years.add(new Year(age, dollars(spending), dollars(pension), dollars(withdrawals[0]), dollars(withdrawals[1]), dollars(withdrawals[2]),
                    dollars(minimum), dollars(tax), dollars(recovery), dollars(shortfall), dollars(balances[0]), dollars(balances[1]), dollars(balances[2])));
        }
        return new Result(List.copyOf(years), dollars(totalTax), dollars(totalRecovery), dollars(totalShortfall), dollars(sum(balances)),
                "Ontario estimate using 2026 brackets, basic personal credits, surtax, health premium and OAS recovery. All thresholds indexed by your inflation assumption. Annual growth precedes annual withdrawals; RRIF conversion at 71, minimums from 72; surplus reinvested in other. Other-account growth is fully taxable interest; withdrawals are principal. No age/pension credits, splitting, capital-gains cost basis, GIS, fees or future contribution-room enforcement. Separate from the monthly before-tax overview.");
    }
    static double rrifFactor(int age) {
        double[] rates = {.0528,.0540,.0553,.0567,.0582,.0598,.0617,.0636,.0658,.0682,.0708,.0738,.0771,.0808,.0851,.0899,.0955,.1021,.1099,.1192,.1306,.1449,.1634,.1879};
        return age >= 95 ? .2 : age >= 71 ? rates[age - 71] : 1.0 / (90 - age);
    }
    static double ontarioTax(double income) {
        double personal = 16452 - 1623 * Math.min(1, Math.max(0, (income - 181440) / (258482 - 181440.0)));
        double federal = Math.max(0, brackets(income,new double[]{58523,117045,181440,258482},new double[]{.14,.205,.26,.29,.33}) - personal * .14);
        double provincial = Math.max(0, brackets(income,new double[]{53891,107785,150000,220000},new double[]{.0505,.0915,.1116,.1216,.1316}) - 12989 * .0505);
        double surtax = Math.max(0,provincial - 5818) * .2 + Math.max(0,provincial - 7446) * .36;
        double beforeReduction = provincial + surtax;
        double reduction = Math.min(beforeReduction, Math.max(0, 600 - beforeReduction));
        double health = income <= 20000 ? 0 : income <= 36000 ? Math.min(300,(income-20000)*.06) : income <= 48000 ? Math.min(450,300+(income-36000)*.06)
                : income <= 72000 ? Math.min(600,450+(income-48000)*.25) : income <= 200000 ? Math.min(750,600+(income-72000)*.25) : Math.min(900,750+(income-200000)*.25);
        return federal + beforeReduction - reduction + health;
    }
    private static double brackets(double income, double[] limits, double[] rates) {
        double tax = 0, previous = 0;
        for (int i = 0; i < rates.length; i++) {
            double limit = i < limits.length ? limits[i] : Double.POSITIVE_INFINITY;
            tax += Math.max(0, Math.min(income,limit)-previous)*rates[i]; previous = limit;
        }
        return tax;
    }
    private static double[] allocate(double[] balances, double minimum, double requested, int[] order) {
        double[] result = {0,minimum,0}; double remaining = Math.max(0,requested-minimum);
        for (int i : order) { double take = Math.min(Math.max(0,balances[i]-result[i]),remaining); result[i] += take; remaining -= take; }
        return result;
    }
    private static double sum(double[] amounts) { return amounts[0]+amounts[1]+amounts[2]; }
    private static BigDecimal dollars(double value) { return money(BigDecimal.valueOf(value)); }
}
