package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanningTests {
    @Autowired RetirementPlanService retirement;
    @Autowired AccountRulesService accounts;
    @Autowired GoalsService goals;
    @Autowired JsonMapper mapper;
    @Autowired MockMvc mvc;

    private static final String ACCOUNT = """
        {"birthDate":"1990-01-01","province":"ON","canadianResident":true,
        "firstResidentYear":1990,"fullNonResidentYears":[],"tfsaLifetimeContributions":0,
        "tfsaWithdrawalsBefore2026":0,"tfsaKnownRemainingRoom":null,"tfsaPlannedContribution":6000,
        "livedInOwnedHome":false,"livedInSpouseOwnedHome":false,"fhsaOpenedYear":null,
        "fhsaFirstWithdrawalYear":null,"fhsaCarryForward":0,"fhsaUsedBefore2026":0,
        "fhsaUsedIn2026":0,"fhsaPlannedContribution":3000,"fhsaComplexHistory":false,
        "fhsaKnownRemainingRoom":null,"previousEarnedIncome":100000,"pensionAdjustment":0,
        "rrspKnownRemainingRoom":null,"rrspPlannedContribution":1000}
        """;
    private static final String PLAN = """
        {"savings":{"currentAge":64,"retirementAge":65,"currentSavings":10000,
        "monthlyContribution":0,"annualReturnRate":0,"annualInflationRate":0},
        "planningAge":67,"monthlySpending":1000,"retirementReturnRate":0,
        "cppMonthlyAt65":0,"cppStartAge":65,"oasMonthlyAt65":0,"oasStartAge":65,
        "otherMonthlyIncome":0,"otherIncomeStartAge":65}
        """;
    private BigDecimal d(String s) { return new BigDecimal(s); }
    @SuppressWarnings("unchecked")
    private <T> T fixture(String json, Map<String, Object> changes, Class<T> type) {
        var data = new HashMap<String, Object>(mapper.readValue(json, Map.class));
        data.putAll(changes); return mapper.convertValue(data, type);
    }
    private AccountRulesResult account(Map<String, Object> changes) { return accounts.assess(fixture(ACCOUNT, changes, AccountRulesRequest.class)); }
    private RetirementPlanResult plan(Map<String, Object> changes) { return retirement.project(fixture(PLAN, changes, RetirementPlanRequest.class)); }

    @Test void modelsShortfallAndSolvesFundingTargets() {
        var r = plan(Map.of());
        assertEquals(d("24000.00"), r.requiredAtRetirement());
        assertEquals(d("14000.00"), r.fundingGap());
        assertEquals(d("1166.67"), r.requiredMonthlyContribution());
        assertEquals(d("416.66"), r.sustainableMonthlySpending());
        assertEquals(65, r.firstShortfallAge()); assertEquals(11, r.firstShortfallMonth());
        assertEquals(d("14000.00"), r.totalUnfundedSpending());
        assertEquals(d("0.00"), r.endingBalance()); assertEquals(2, r.retirementYears().size());
        assertEquals(d("10000.00"), r.retirementYears().getFirst().withdrawals());
        assertEquals(d("2000.00"), r.retirementYears().getFirst().unfundedSpending());
    }
    @Test void reachingZeroExactlyAtHorizonIsNotAShortfall() {
        var r = retirement.project(mapper.readValue(PLAN.replace("\"currentSavings\":10000", "\"currentSavings\":24000"), RetirementPlanRequest.class));
        assertNull(r.firstShortfallAge());
    }
    @Test void incomeOffsetsWithdrawalsOnlyOnceItStarts() {
        var r = plan(Map.of("otherMonthlyIncome", 1000, "otherIncomeStartAge", 66));
        assertEquals(d("12000.00"), r.requiredAtRetirement());
        assertEquals(d("0.00"), r.retirementYears().getFirst().pensionIncome());
        assertEquals(d("12000.00"), r.retirementYears().getLast().pensionIncome());
    }
    @ParameterizedTest @CsvSource({"60,640.00", "65,1000.00", "70,1420.00"})
    void cppStartAgeAdjustment(int age, String expected) {
        assertEquals(d(expected), plan(Map.of("cppMonthlyAt65", 1000, "cppStartAge", age)).adjustedCppMonthly());
    }
    @Test void oasDeferralAdjustment() { assertEquals(d("1360.00"), plan(Map.of("oasMonthlyAt65", 1000, "oasStartAge", 70)).adjustedOasMonthly()); }
    @Test void modelsInflationCumulativelyAndNegativeReturns() {
        var negative = plan(Map.of("retirementReturnRate", -.1));
        assertTrue(negative.requiredAtRetirement().compareTo(d("24000")) > 0);
        assertTrue(negative.retirementYears().getFirst().investmentGrowth().signum() < 0);
        var req = mapper.readValue(PLAN.replace("\"annualInflationRate\":0", "\"annualInflationRate\":0.10"), RetirementPlanRequest.class);
        var inflated = retirement.project(req);
        assertTrue(inflated.retirementYears().getLast().spending().compareTo(inflated.retirementYears().getFirst().spending()) > 0);
    }
    @Test void monthlyContributionTargetActuallyFundsThePlan() {
        var base = mapper.readValue(PLAN, RetirementPlanRequest.class);
        var result = plan(Map.of());
        var s = base.savings();
        var funded = retirement.project(new RetirementPlanRequest(new ProjectionRequest(s.currentAge(), s.retirementAge(), s.currentSavings(), result.requiredMonthlyContribution(), s.annualReturnRate(), s.annualInflationRate()), base.planningAge(), base.monthlySpending(), base.retirementReturnRate(), base.cppMonthlyAt65(), base.cppStartAge(), base.oasMonthlyAt65(), base.oasStartAge(), base.otherMonthlyIncome(), base.otherIncomeStartAge()));
        assertNull(funded.firstShortfallAge()); assertTrue(funded.endingBalance().signum() >= 0);
    }
    @ParameterizedTest @ValueSource(strings = {"planningAge", "monthlySpending", "retirementReturnRate", "cppMonthlyAt65", "cppStartAge", "oasMonthlyAt65", "oasStartAge", "otherMonthlyIncome", "otherIncomeStartAge"})
    void requiresPlanFields(String field) {
        var change = new HashMap<String, Object>(); change.put(field, null);
        assertThrows(IllegalArgumentException.class, () -> plan(change));
    }
    @Test void rejectsInvalidPlanValues() {
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of("planningAge", 65)));
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of("monthlySpending", -1)));
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of("cppStartAge", 59)));
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of("oasStartAge", 64)));
        assertThrows(IllegalArgumentException.class, () -> plan(Map.of("retirementReturnRate", -1)));
    }
    @Test void tfsaHistoricalLimitsSumTo109000() { assertEquals(d("109000.00"), account(Map.of()).tfsa().remainingRoom()); }
    @Test void tfsaAccountsForResidencyContributionsAndPriorWithdrawals() {
        var r = account(Map.of("firstResidentYear", 2023, "fullNonResidentYears", java.util.List.of(2024), "tfsaLifetimeContributions", 10000, "tfsaWithdrawalsBefore2026", 1000));
        assertEquals(d("11500.00"), r.tfsa().remainingRoom());
    }
    @Test void knownTfsaRoomOverridesHistoricalEstimate() {
        var r = account(Map.of("tfsaKnownRemainingRoom", 2000));
        assertEquals(d("2000.00"), r.tfsa().remainingRoom()); assertEquals(d("4000.00"), r.tfsa().plannedOverage());
    }
    @Test void provincialOpeningAgeDoesNotPreventTfsaRoomAccrual() {
        var r = account(Map.of("birthDate", "2008-01-01", "firstResidentYear", 2008, "province", "BC"));
        assertEquals(19, r.legalOpeningAge()); assertEquals(d("7000.00"), r.tfsa().remainingRoom());
        assertEquals(d("6000.00"), r.tfsa().plannedOverage()); assertEquals("Not eligible to open", r.fhsa().status());
    }
    @Test void nonResidentContributionsAreFlaggedEvenWithRoom() {
        var r = account(Map.of("canadianResident", false));
        assertEquals(d("6000.00"), r.tfsa().plannedOverage()); assertEquals(d("0.00"), r.fhsa().remainingRoom());
    }
    @Test void unopenedFhsaHasNoCarryforward() {
        assertEquals("Eligible to open", account(Map.of()).fhsa().status());
        assertEquals(d("8000.00"), account(Map.of()).fhsa().remainingRoom());
        assertThrows(IllegalArgumentException.class, () -> account(Map.of("fhsaCarryForward", 1000)));
    }
    @Test void fhsaCapsBothAnnualAndLifetimeRoomAndIncludesTransfers() {
        var r = account(Map.of("fhsaOpenedYear", 2023, "fhsaCarryForward", 8000, "fhsaUsedBefore2026", 39000, "fhsaUsedIn2026", 500));
        assertEquals(d("500.00"), r.fhsa().remainingRoom()); assertEquals(d("2500.00"), r.fhsa().plannedOverage());
        assertEquals(2038, r.fhsa().closingYear());
    }
    @Test void ownedAndSpouseOwnedHomeAffectNewOpeningButNotExistingAccount() {
        assertEquals("Not eligible to open", account(Map.of("livedInSpouseOwnedHome", true)).fhsa().status());
        assertEquals("Not eligible to open", account(Map.of("livedInOwnedHome", true)).fhsa().status());
        assertEquals("Existing account", account(Map.of("livedInOwnedHome", true, "fhsaOpenedYear", 2024)).fhsa().status());
    }
    @Test void fhsaUsesDecember31AgeAndQualifyingWithdrawalDeadline() {
        assertEquals("Not eligible to open", account(Map.of("birthDate", "1954-12-01", "firstResidentYear", 1954)).fhsa().status());
        var r = account(Map.of("fhsaOpenedYear", 2023, "fhsaFirstWithdrawalYear", 2025));
        assertEquals(2026, r.fhsa().closingYear()); assertEquals(d("0.00"), r.fhsa().remainingRoom());
    }
    @Test void complexFhsaHistoryRequiresVerifiedRoom() {
        assertNull(account(Map.of("fhsaOpenedYear", 2023, "fhsaComplexHistory", true)).fhsa().remainingRoom());
        assertEquals(d("9500.00"), account(Map.of("fhsaOpenedYear", 2023, "fhsaComplexHistory", true, "fhsaKnownRemainingRoom", 9500)).fhsa().remainingRoom());
    }
    @Test void rrspDoesNotPretendNewRoomIsActualRemainingRoom() {
        assertNull(account(Map.of()).rrsp().remainingRoom());
        assertEquals(d("15000.00"), account(Map.of("rrspKnownRemainingRoom", 15000)).rrsp().remainingRoom());
        assertEquals(d("0.00"), account(Map.of("birthDate", "1950-01-01", "firstResidentYear", 1950, "rrspKnownRemainingRoom", 15000)).rrsp().remainingRoom());
    }
    @Test void accountValidation() {
        assertThrows(IllegalArgumentException.class, () -> accounts.assess(null));
        assertThrows(IllegalArgumentException.class, () -> account(Map.of("province", "XX")));
        assertThrows(IllegalArgumentException.class, () -> account(Map.of("fhsaOpenedYear", 2023, "fhsaCarryForward", 8001)));
        assertThrows(IllegalArgumentException.class, () -> account(Map.of("tfsaLifetimeContributions", -1)));
        assertThrows(IllegalArgumentException.class, () -> account(Map.of("fullNonResidentYears", java.util.List.of(2026))));
    }
    @Test void homeGoalTarget() {
        var r = goals.home(new GoalsService.HomeRequest(d("10000"), d("500"), d("40000"), 5, d("0"), d("0")));
        assertEquals(d("40000.00"), r.projection().finalBalance()); assertEquals(d("500.00"), r.requiredMonthlyContribution());
    }
    @Test void budgetTracksCashflowEmergencyAndDebt() {
        var r = goals.budget(new GoalsService.BudgetRequest(d("5000"), d("2000"), d("500"), d("500"), d("3000"), 6, d("1200"), d("0"), d("100")));
        assertEquals(d("1900.00"), r.monthlyUnallocated()); assertEquals(d("9000.00"), r.emergencyGap());
        assertEquals(d("1.50"), r.emergencyMonthsCovered()); assertEquals(12, r.debtPayoffMonths());
    }
    @Test void debtPaymentMustCoverInterest() {
        var r = goals.budget(new GoalsService.BudgetRequest(d("0"), d("0"), d("0"), d("0"), d("0"), 3, d("1200"), d(".12"), d("12")));
        assertTrue(r.debtPaymentTooLow()); assertNull(r.debtPayoffMonths()); assertNull(r.emergencyMonthsCovered());
    }
    @Test void planningApisUseRealServicesAndReturnClearErrors() throws Exception {
        mvc.perform(post("/api/plans/retirement").contentType("application/json").content(PLAN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiredAtRetirement").value(24000));
        mvc.perform(post("/api/plans/accounts").contentType("application/json").content(ACCOUNT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tfsa.remainingRoom").value(109000));
        mvc.perform(post("/api/plans/retirement").contentType("application/json").content(PLAN.replace("\"planningAge\":67", "\"planningAge\":64")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(post("/api/plans/accounts").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("birthDate is required"));
    }
    @Test void csvIncludesSavingsAndRetirementWithExactCents() throws Exception {
        mvc.perform(post("/api/plans/retirement/csv").contentType("application/x-www-form-urlencoded").param("plan", PLAN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"dream-planner-annual-plan.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("65,Saving,0.00,0.00,0.00,0.00,10000.00,10000.00,0.00\r\n")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("66,Retirement,0.00,0.00,10000.00,0.00,0.00,0.00,2000.00\r\n")));
    }
    @Test void csvValidatesMalformedAndInvalidPlans() throws Exception {
        mvc.perform(post("/api/plans/retirement/csv").contentType("application/x-www-form-urlencoded").param("plan", "{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(post("/api/plans/retirement/csv").contentType("application/x-www-form-urlencoded").param("plan", PLAN.replace("\"planningAge\":67", "\"planningAge\":64")))
                .andExpect(status().isBadRequest());
    }
    @Test void oasIncreasesAt75AndSurplusIncomeDoesNotInflateSavings() {
        var r = retirement.project(mapper.readValue(PLAN.replace("\"currentAge\":64", "\"currentAge\":73").replace("\"retirementAge\":65", "\"retirementAge\":74").replace("\"planningAge\":67", "\"planningAge\":76").replace("\"oasMonthlyAt65\":0", "\"oasMonthlyAt65\":1000"), RetirementPlanRequest.class));
        assertEquals(d("12000.00"), r.retirementYears().getFirst().pensionIncome());
        assertEquals(d("13200.00"), r.retirementYears().getLast().pensionIncome());
        assertEquals(d("10000.00"), r.endingBalance());
        assertNull(r.firstShortfallAge());
    }
    @Test void planExportPreservesInputsAsAnAttachment() throws Exception {
        mvc.perform(post("/api/plans/export").contentType("application/x-www-form-urlencoded")
                        .param("plan", "{\"version\":1,\"assessmentDate\":\"2026-09-29\",\"plan\":{\"tfsaBalance\":1234.56,\"canadianResident\":true}}"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=\"dream-planner-plan.json\""))
                .andExpect(jsonPath("$.plan.tfsaBalance").value(1234.56)).andExpect(jsonPath("$.plan.canadianResident").value(true));
    }
    @Test void planExportRejectsMalformedOrUnversionedFiles() throws Exception {
        for (String invalid : java.util.List.of("null", "{broken", "{}", "{\"version\":2,\"plan\":{}}"))
            mvc.perform(post("/api/plans/export").contentType("application/x-www-form-urlencoded").param("plan", invalid))
                    .andExpect(status().isBadRequest());
    }
}
