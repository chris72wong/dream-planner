package com.example.retirement_planner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import static com.example.retirement_planner.PlanMath.*;

/** Rebuild every calculator request from the saved plan rather than trusting a browser result. */
@Component
public class PlanDocumentValidator {
    private final RetirementPlanService retirement;
    private final AccountRulesService accounts;
    private final GoalsService goals;
    private final JsonMapper mapper;
    public PlanDocumentValidator(RetirementPlanService retirement, AccountRulesService accounts, GoalsService goals, JsonMapper mapper) {
        this.retirement=retirement; this.accounts=accounts; this.goals=goals; this.mapper=mapper;
    }
    public RetirementPlanRequest validate(Map<String,Object> document) {
        if(document==null||!Integer.valueOf(1).equals(document.get("version"))||!AccountRulesService.AS_OF.toString().equals(document.get("assessmentDate"))
                || !(document.get("plan") instanceof Map<?,?> p)) throw new IllegalArgumentException("Send a complete Dream Planner 2026 plan document.");
        // Optional histories must still be present so incomplete documents cannot silently acquire defaults.
        String[] keys=("birthDate province canadianResident firstResidentYear fullNonResidentYears tfsaBalance rrspBalance otherBalance monthlyContribution retirementAge planningAge monthlySpending annualReturn inflation retirementReturn cppMonthlyAt65 cppStartAge oasMonthlyAt65 oasStartAge otherMonthlyIncome otherIncomeStartAge monthlyTakeHome monthlyExpenses emergencyCash emergencyMonths debtBalance debtApr debtPayment fhsaBalance homeCash homeMonthly homeTarget homeYears homeReturn tfsaLifetimeContributions tfsaWithdrawalsBefore2026 tfsaKnownRemainingRoom tfsaPlannedContribution livedInOwnedHome livedInSpouseOwnedHome fhsaOpenedYear fhsaFirstWithdrawalYear fhsaCarryForward fhsaUsedBefore2026 fhsaUsedIn2026 fhsaPlannedContribution fhsaComplexHistory fhsaKnownRemainingRoom previousEarnedIncome pensionAdjustment rrspKnownRemainingRoom rrspPlannedContribution").split(" ");
        for(var key:keys) if(!p.containsKey(key)) throw new IllegalArgumentException("Plan is missing "+key+".");
        if(!(p.get("birthDate") instanceof String birth)) throw new IllegalArgumentException("Enter a valid birth date.");
        LocalDate date;
        try { date=LocalDate.parse(birth); } catch(RuntimeException ex) { throw new IllegalArgumentException("Enter a valid birth date."); }
        var s=new ProjectionRequest(Period.between(date,AccountRulesService.AS_OF).getYears(),integer(p,"retirementAge"),
                number(p,"tfsaBalance").add(number(p,"rrspBalance")).add(number(p,"otherBalance")),number(p,"monthlyContribution"),rate(p,"annualReturn"),rate(p,"inflation"));
        for(String key:new String[]{"tfsaBalance","rrspBalance","otherBalance","fhsaBalance","homeCash"}) amount(number(p,key),key);
        var retirementRequest=new RetirementPlanRequest(s,integer(p,"planningAge"),number(p,"monthlySpending"),rate(p,"retirementReturn"),
                number(p,"cppMonthlyAt65"),integer(p,"cppStartAge"),number(p,"oasMonthlyAt65"),integer(p,"oasStartAge"),number(p,"otherMonthlyIncome"),integer(p,"otherIncomeStartAge"));
        retirement.project(retirementRequest);
        var accountData=new HashMap<String,Object>();
        for(var component:AccountRulesRequest.class.getRecordComponents()) accountData.put(component.getName(),p.get(component.getName()));
        if(!(p.get("fullNonResidentYears") instanceof String years)||!years.isBlank()&&!years.matches("\\d{4}(\\s*,\\s*\\d{4})*"))
            throw new IllegalArgumentException("Enter non-resident years separated by commas.");
        accountData.put("fullNonResidentYears",years.isBlank()?java.util.List.of():Arrays.stream(years.split(",")).map(String::trim).map(Integer::valueOf).toList());
        for(String key:new String[]{"canadianResident","fhsaComplexHistory"}) if(!(p.get(key) instanceof Boolean)) throw new IllegalArgumentException(key+" must be a boolean.");
        try { accounts.assess(mapper.convertValue(accountData,AccountRulesRequest.class)); }
        catch(IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid account history: "+ex.getMessage()); }
        goals.home(new GoalsService.HomeRequest(number(p,"fhsaBalance").add(number(p,"homeCash")),number(p,"homeMonthly"),number(p,"homeTarget"),integer(p,"homeYears"),rate(p,"homeReturn"),rate(p,"inflation")));
        goals.budget(new GoalsService.BudgetRequest(number(p,"monthlyTakeHome"),number(p,"monthlyExpenses"),number(p,"monthlyContribution"),number(p,"homeMonthly"),
                number(p,"emergencyCash"),integer(p,"emergencyMonths"),number(p,"debtBalance"),rate(p,"debtApr"),number(p,"debtPayment")));
        return retirementRequest;
    }
    private static BigDecimal number(Map<?,?> p,String key) {
        if(!(p.get(key) instanceof Number n)) throw new IllegalArgumentException(key+" must be a number.");
        try { return new BigDecimal(n.toString()); } catch(RuntimeException ex) { throw new IllegalArgumentException("Invalid "+key+"."); }
    }
    private static int integer(Map<?,?> p,String key) {
        try { return number(p,key).intValueExact(); } catch(ArithmeticException ex) { throw new IllegalArgumentException(key+" must be a whole number."); }
    }
    private static BigDecimal rate(Map<?,?> p,String key) { return number(p,key).divide(new BigDecimal("100")); }
}
