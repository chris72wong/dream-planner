package com.example.retirement_planner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Dollar histories exclude direct account-to-account transfers except RRSP-to-FHSA transfers. */
public record AccountRulesRequest(LocalDate birthDate, String province, Boolean canadianResident,
        Integer firstResidentYear, Set<Integer> fullNonResidentYears,
        BigDecimal tfsaLifetimeContributions, BigDecimal tfsaWithdrawalsBefore2026,
        BigDecimal tfsaKnownRemainingRoom, BigDecimal tfsaPlannedContribution,
        Boolean livedInOwnedHome, Boolean livedInSpouseOwnedHome,
        Integer fhsaOpenedYear, Integer fhsaFirstWithdrawalYear, BigDecimal fhsaCarryForward,
        BigDecimal fhsaUsedBefore2026, BigDecimal fhsaUsedIn2026, BigDecimal fhsaPlannedContribution,
        Boolean fhsaComplexHistory, BigDecimal fhsaKnownRemainingRoom,
        BigDecimal previousEarnedIncome, BigDecimal pensionAdjustment,
        BigDecimal rrspKnownRemainingRoom, BigDecimal rrspPlannedContribution) { }
