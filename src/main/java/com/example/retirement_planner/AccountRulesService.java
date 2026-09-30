package com.example.retirement_planner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import static com.example.retirement_planner.PlanMath.*;

/** A versioned 2026 assessment, deliberately not extrapolated to later tax years. */
@Service
public class AccountRulesService {
    public static final LocalDate AS_OF = LocalDate.of(2026, 9, 29);
    private static final Set<String> PROVINCES = Set.of("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT");
    private static final Set<String> AGE_19 = Set.of("BC", "NB", "NL", "NS", "NT", "NU", "YT");
    private static final String TFSA_SOURCE = "https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/tax-free-savings-account/contributing/calculate-room.html";
    private static final String FHSA_SOURCE = "https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account.html";
    private static final String RRSP_SOURCE = "https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/rrsps-related-plans/contributing-a-rrsp-prpp/contributions-affect-your-rrsp-prpp-deduction-limit.html";

    public AccountRulesResult assess(AccountRulesRequest r) {
        require(r, "account profile"); require(r.birthDate(), "birthDate");
        if (r.birthDate().isAfter(AS_OF) || r.birthDate().isBefore(AS_OF.minusYears(120))) throw new IllegalArgumentException("birthDate must be within the last 120 years of the assessment date");
        require(r.province(), "province");
        if (!PROVINCES.contains(r.province())) throw new IllegalArgumentException("province must be a Canadian province or territory code");
        require(r.canadianResident(), "canadianResident"); require(r.fullNonResidentYears(), "fullNonResidentYears");
        range(r.firstResidentYear(), r.birthDate().getYear(), 2026, "firstResidentYear");
        for (Integer year : r.fullNonResidentYears()) range(year, r.firstResidentYear(), 2026, "fullNonResidentYears");
        if (r.canadianResident() && r.fullNonResidentYears().contains(2026)) throw new IllegalArgumentException("A current resident cannot be non-resident for all of 2026");
        amount(r.tfsaLifetimeContributions(), "tfsaLifetimeContributions"); amount(r.tfsaWithdrawalsBefore2026(), "tfsaWithdrawalsBefore2026");
        amount(r.tfsaPlannedContribution(), "tfsaPlannedContribution"); optionalAmount(r.tfsaKnownRemainingRoom(), "tfsaKnownRemainingRoom");
        amount(r.fhsaCarryForward(), "fhsaCarryForward"); amount(r.fhsaUsedBefore2026(), "fhsaUsedBefore2026"); amount(r.fhsaUsedIn2026(), "fhsaUsedIn2026");
        amount(r.fhsaPlannedContribution(), "fhsaPlannedContribution"); optionalAmount(r.fhsaKnownRemainingRoom(), "fhsaKnownRemainingRoom");
        require(r.fhsaComplexHistory(), "fhsaComplexHistory");
        if (r.fhsaCarryForward().compareTo(new BigDecimal("8000")) > 0) throw new IllegalArgumentException("fhsaCarryForward cannot exceed 8000");
        if (r.fhsaOpenedYear() != null) range(r.fhsaOpenedYear(), Math.max(2023, r.birthDate().getYear() + 18), 2026, "fhsaOpenedYear");
        if (r.fhsaFirstWithdrawalYear() != null) {
            require(r.fhsaOpenedYear(), "fhsaOpenedYear for a prior qualifying withdrawal");
            range(r.fhsaFirstWithdrawalYear(), r.fhsaOpenedYear(), 2026, "fhsaFirstWithdrawalYear");
        }
        if ((r.fhsaOpenedYear() == null || r.fhsaOpenedYear() == 2026) && (r.fhsaCarryForward().signum() > 0 || r.fhsaUsedBefore2026().signum() > 0))
            throw new IllegalArgumentException("FHSA history before 2026 requires an account opened before 2026");
        if (r.fhsaOpenedYear() == null && r.fhsaUsedIn2026().signum() > 0) throw new IllegalArgumentException("FHSA contributions require an opening year");
        amount(r.previousEarnedIncome(), "previousEarnedIncome"); amount(r.pensionAdjustment(), "pensionAdjustment");
        optionalAmount(r.rrspKnownRemainingRoom(), "rrspKnownRemainingRoom"); amount(r.rrspPlannedContribution(), "rrspPlannedContribution");
        int age = Period.between(r.birthDate(), AS_OF).getYears();
        int yearEndAge = 2026 - r.birthDate().getYear();
        int legalAge = AGE_19.contains(r.province()) ? 19 : 18;

        var tfsaNotes = new ArrayList<String>();
        var accumulated = ZERO;
        for (int year = Math.max(2009, Math.max(r.birthDate().getYear() + 18, r.firstResidentYear())); year <= 2026; year++)
            if (!r.fullNonResidentYears().contains(year)) accumulated = accumulated.add(BigDecimal.valueOf(tfsaLimit(year)));
        var tfsaRoom = r.tfsaKnownRemainingRoom() != null ? r.tfsaKnownRemainingRoom()
                : accumulated.add(r.tfsaWithdrawalsBefore2026()).subtract(r.tfsaLifetimeContributions());
        String tfsaStatus = !r.canadianResident() ? "Non-resident" : age < 18 ? "Not yet eligible" : age < legalAge ? "Room accrues; opening at " + legalAge : "Eligible";
        tfsaNotes.add(r.tfsaKnownRemainingRoom() == null ? "Estimated from eligible resident years and your history. Reconcile with all institution records; CRA figures may lag." : "Uses the remaining room you entered, after contributions already made.");
        tfsaNotes.add("Withdrawals made in 2026 return as room on January 1, 2027. Investment growth does not use room.");
        if (!r.canadianResident()) tfsaNotes.add("Contributions while non-resident can trigger tax even with unused room. No contribution is allocated here.");
        if (age < legalAge) tfsaNotes.add("Room starts in the year you turn 18; opening an account may have to wait until age " + legalAge + ".");
        if (tfsaRoom.signum() < 0) tfsaNotes.add("Your history indicates a possible excess contribution of $" + money(tfsaRoom.negate()) + ". Review your records.");
        var tfsaAllowed = r.canadianResident() && age >= legalAge ? tfsaRoom.max(ZERO) : ZERO;

        var fhsaNotes = new ArrayList<String>();
        boolean knownEligibility = r.livedInOwnedHome() != null && r.livedInSpouseOwnedHome() != null;
        boolean canOpen = age >= legalAge && yearEndAge <= 71 && r.canadianResident() && knownEligibility
                && !r.livedInOwnedHome() && !r.livedInSpouseOwnedHome();
        boolean hasAccount = r.fhsaOpenedYear() != null;
        Integer closing = hasAccount ? Math.min(r.fhsaOpenedYear() + 15, r.birthDate().getYear() + 71) : null;
        if (r.fhsaFirstWithdrawalYear() != null) closing = Math.min(closing, r.fhsaFirstWithdrawalYear() + 1);
        boolean active = hasAccount && closing >= 2026;
        String fhsaStatus = hasAccount ? (active ? "Existing account" : "Participation ended")
                : canOpen ? "Eligible to open" : !knownEligibility ? "Check home history" : "Not eligible to open";
        BigDecimal fhsaRoom = null;
        if (!hasAccount && !canOpen || hasAccount && !active) fhsaRoom = ZERO;
        else if (r.fhsaKnownRemainingRoom() != null) fhsaRoom = r.fhsaKnownRemainingRoom();
        else if (!r.fhsaComplexHistory()) fhsaRoom = new BigDecimal("8000").add(hasAccount ? r.fhsaCarryForward() : ZERO)
                .min(new BigDecimal("40000").subtract(r.fhsaUsedBefore2026()).max(ZERO)).subtract(r.fhsaUsedIn2026());
        fhsaNotes.add(hasAccount ? "Contribution room starts in the first opening year; an existing holder does not need to re-pass the home-ownership test each year." : "Opening requires residency, legal age, age 71 or younger at December 31, and both home-history conditions.");
        fhsaNotes.add("The home-history test covers the current year and previous four calendar years, including a home owned by your spouse that you lived in.");
        if (!hasAccount && canOpen) fhsaNotes.add("The shown $8,000 becomes available only when your first FHSA is opened.");
        if (r.fhsaComplexHistory()) fhsaNotes.add("Past excess amounts, taxable withdrawals or designated corrections need your verified remaining participation room; the simple estimate is disabled.");
        if (r.fhsaFirstWithdrawalYear() != null) {
            fhsaNotes.add("Contributions after a first qualifying withdrawal are not deductible. Further contributions are excluded from this planner.");
            fhsaRoom = ZERO;
        }
        if (fhsaRoom != null && fhsaRoom.signum() < 0) fhsaNotes.add("Reported contributions/transfers exceed the simple available-room estimate. Check CRA's detailed calculation.");
        fhsaNotes.add("RRSP transfers use FHSA participation room and are not deductible. Qualifying home withdrawals have a separate eligibility test.");
        if (closing != null) fhsaNotes.add("Close by December 31, " + closing + ". Eligible direct RRSP/RRIF transfers generally do not use RRSP room.");

        var newRrspRoom = r.previousEarnedIncome().multiply(new BigDecimal("0.18")).min(new BigDecimal("33810")).subtract(r.pensionAdjustment()).max(ZERO);
        var rrspRoom = yearEndAge <= 71 ? r.rrspKnownRemainingRoom() : ZERO;
        var rrspNotes = new ArrayList<String>();
        rrspNotes.add("Basic new 2026 room estimate: $" + money(newRrspRoom) + ", from 18% of 2025 earned income, capped at $33,810, less pension adjustment.");
        rrspNotes.add("Use your actual remaining contribution room, not just the deduction limit. Carryforward, unused contributions, PAR and PSPA can change it.");
        rrspNotes.add("Your own RRSP must mature by December 31 of the year you turn 71. Spousal RRSP rules are not assessed here.");
        return new AccountRulesResult(2026, AS_OF, age, legalAge,
                new AccountRulesResult.Account("TFSA", tfsaStatus, money(tfsaRoom.max(ZERO)), money(r.tfsaPlannedContribution().subtract(tfsaAllowed).max(ZERO)), new BigDecimal("7000.00"), null, null, tfsaNotes, TFSA_SOURCE),
                new AccountRulesResult.Account("FHSA", fhsaStatus, fhsaRoom == null ? null : money(fhsaRoom.max(ZERO)), fhsaRoom == null ? null : money(r.fhsaPlannedContribution().subtract(fhsaRoom.max(ZERO)).max(ZERO)), new BigDecimal("8000.00"), new BigDecimal("40000.00"), closing, fhsaNotes, FHSA_SOURCE),
                new AccountRulesResult.Account("RRSP", yearEndAge > 71 ? "Own RRSP matured" : rrspRoom == null ? "Enter verified room" : "Room entered", rrspRoom == null ? null : money(rrspRoom), rrspRoom == null ? null : money(r.rrspPlannedContribution().subtract(rrspRoom).max(ZERO)), new BigDecimal("33810.00"), null, r.birthDate().getYear() + 71, rrspNotes, RRSP_SOURCE));
    }

    private void optionalAmount(BigDecimal value, String field) { if (value != null) amount(value, field); }
    static int tfsaLimit(int year) {
        if (year < 2009 || year > 2026) throw new IllegalArgumentException("No TFSA rules for " + year);
        if (year <= 2012) return 5000;
        if (year == 2015) return 10000;
        if (year <= 2018) return 5500;
        if (year <= 2022) return 6000;
        return year == 2023 ? 6500 : 7000;
    }
}
