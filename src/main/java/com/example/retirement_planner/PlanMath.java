package com.example.retirement_planner;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

final class PlanMath {
    static final MathContext MC = MathContext.DECIMAL128;
    static final BigDecimal ZERO = BigDecimal.ZERO;
    static final BigDecimal ONE = BigDecimal.ONE;
    private PlanMath() { }
    static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    static BigDecimal ceilMoney(BigDecimal value) { return value.setScale(2, RoundingMode.CEILING); }
    static void require(Object value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }
    static void range(Integer value, int min, int max, String field) {
        require(value, field);
        if (value < min || value > max) throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
    }
    static void amount(BigDecimal value, String field) {
        require(value, field);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000000")) > 0 || value.scale() > 8)
            throw new IllegalArgumentException(field + " must be between 0 and 1 billion, with at most 8 decimal places");
    }
    static BigDecimal factor(BigDecimal annualRate, String field) {
        require(annualRate, field);
        if (annualRate.compareTo(ONE.negate()) <= 0 || annualRate.compareTo(ONE) > 0)
            throw new IllegalArgumentException(field + " must be greater than -1 and no greater than 1");
        // Fractional powers use double; all balances and subsequent arithmetic use DECIMAL128.
        double factor = StrictMath.exp(StrictMath.log1p(annualRate.doubleValue()) / 12.0);
        if (!Double.isFinite(factor) || factor == 0)
            throw new IllegalArgumentException(field + " is too close to -1 to model reliably");
        return BigDecimal.valueOf(factor);
    }
}
