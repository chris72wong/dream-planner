package com.example.retirement_planner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AccountRulesResult(int ruleYear, LocalDate verifiedOn, int age, int legalOpeningAge,
        Account tfsa, Account fhsa, Account rrsp) {
    public record Account(String name, String status, BigDecimal remainingRoom, BigDecimal plannedOverage,
            BigDecimal annualLimit, BigDecimal lifetimeLimit, Integer closingYear, List<String> notes, String source) {
        public Account { notes = List.copyOf(notes); }
    }
}
