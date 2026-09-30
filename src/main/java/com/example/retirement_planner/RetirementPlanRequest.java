package com.example.retirement_planner;

import java.math.BigDecimal;

public record RetirementPlanRequest(
        ProjectionRequest savings, Integer planningAge, BigDecimal monthlySpending,
        BigDecimal retirementReturnRate, BigDecimal cppMonthlyAt65, Integer cppStartAge,
        BigDecimal oasMonthlyAt65, Integer oasStartAge, BigDecimal otherMonthlyIncome,
        Integer otherIncomeStartAge) { }
