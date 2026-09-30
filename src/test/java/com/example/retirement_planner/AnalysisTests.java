package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisTests {
    private final RetirementPlanService retirement = new RetirementPlanService(new ProjectionService());
    private final RiskService risk = new RiskService(retirement);
    private final DrawdownService drawdown = new DrawdownService(retirement);
    static BigDecimal d(String value) { return new BigDecimal(value); }
    static RetirementPlanRequest plan(int current,int retire,int end,String savings,String saving,String spending) {
        return new RetirementPlanRequest(new ProjectionRequest(current,retire,d(savings),d(saving),d("0"),d("0")),end,d(spending),d("0"),d("0"),65,d("0"),65,d("0"),65);
    }
    @Test void zeroVolatilityReproducesDeterministicModel() {
        var p=plan(64,65,67,"50000","500","1000");var baseline=retirement.project(p);
        var r=risk.simulate(new RiskService.Request(p,d("0"),100,42L));
        assertEquals(baseline.savings().finalBalance(),r.retirementP50());
        assertEquals(baseline.endingBalance(),r.endingP50());assertEquals(d("100.00"),r.successPercent());
        assertEquals(r.retirementP10(),r.retirementP90());
    }
    @Test void detectsShortfallsEvenWhenLaterBalancesRecover() {
        var r=risk.simulate(new RiskService.Request(plan(64,65,67,"10000","0","1000"),d("0"),100,42L));
        assertEquals(d("0.00"),r.successPercent());assertEquals(d("0.00"),r.endingP90());
    }
    @Test void seededSimulationIsRepeatableAndQuantilesOrdered() {
        var request=new RiskService.Request(plan(30,65,95,"10000","500","3500"),d("0.12"),100,2026L);
        var a=risk.simulate(request);assertEquals(a,risk.simulate(request));
        assertTrue(a.retirementP10().compareTo(a.retirementP50())<=0);assertTrue(a.retirementP50().compareTo(a.retirementP90())<=0);
    }
    @Test void rejectsUnboundedSimulationWork() {
        var p=plan(64,65,67,"10000","0","1000");
        assertThrows(IllegalArgumentException.class,()->risk.simulate(new RiskService.Request(p,d(".61"),100,1L)));
        assertThrows(IllegalArgumentException.class,()->risk.simulate(new RiskService.Request(p,d(".1"),2001,1L)));
    }
    @Test void tfsaWithdrawalsDoNotCreateIncomeTax() {
        var p=plan(64,65,67,"100000","0","3500");
        var r=drawdown.project(new DrawdownService.Request(p,"ON",d("100000"),d("0"),d("0"),d("0"),d("1"),"TFSA_FIRST"));
        assertEquals(d("0.00"),r.totalTax());assertEquals(d("16000.00"),r.endingBalance());assertEquals(d("0.00"),r.totalShortfall());
    }
    @Test void rrspWithdrawalsAreGrossedUpToMeetSpending() {
        var p=plan(64,65,67,"200000","0","3500");
        var r=drawdown.project(new DrawdownService.Request(p,"ON",d("0"),d("200000"),d("0"),d("0"),d("0"),"RRSP_FIRST"));
        var year=r.years().getFirst();assertTrue(year.incomeTax().signum()>0);
        assertEquals(d("42000.00"),year.rrspWithdrawal().subtract(year.incomeTax()));
        assertEquals(d("0.00"),r.totalShortfall());
    }
    @Test void rrifMinimumIsTakenAndUnspentProceedsReinvested() {
        var p=plan(71,72,73,"100000","0","0");
        var r=drawdown.project(new DrawdownService.Request(p,"ON",d("0"),d("100000"),d("0"),d("0"),d("0"),"TFSA_FIRST"));
        var year=r.years().getFirst();assertEquals(d("5400.00"),year.rrifMinimum());assertEquals(d("5400.00"),year.rrspWithdrawal());
        assertEquals(d("5400.00"),year.other());assertEquals(d("100000.00"),r.endingBalance());
    }
    @Test void taxAndRecoveryRemainFundedOrReportedAsShortfall() {
        var p=plan(64,65,67,"1000","0","3500");
        var r=drawdown.project(new DrawdownService.Request(p,"ON",d("0"),d("1000"),d("0"),d("0"),d("0"),"RRSP_FIRST"));
        assertEquals(d("83000.00"),r.totalShortfall());assertEquals(d("0.00"),r.endingBalance());
    }
    @Test void unsupportedProvincesAndInconsistentAllocationsFailClearly() {
        var p=plan(64,65,67,"10000","0","1000");
        assertThrows(IllegalArgumentException.class,()->drawdown.project(new DrawdownService.Request(p,"BC",d("10000"),d("0"),d("0"),d("0"),d("1"),"TFSA_FIRST")));
        assertThrows(IllegalArgumentException.class,()->drawdown.project(new DrawdownService.Request(p,"ON",d("1"),d("0"),d("0"),d("0"),d("1"),"TFSA_FIRST")));
        assertThrows(IllegalArgumentException.class,()->drawdown.project(new DrawdownService.Request(p,"ON",d("10000"),d("0"),d("0"),d("1"),d("1"),"TFSA_FIRST")));
    }
    @Test void taxBoundariesAndRrifFactors() {
        assertEquals(0,DrawdownService.ontarioTax(0));assertEquals(0,DrawdownService.ontarioTax(10000));
        assertEquals(.054,DrawdownService.rrifFactor(72));assertEquals(.2,DrawdownService.rrifFactor(100));
        assertEquals(7165.7755,DrawdownService.ontarioTax(50000),.0001);
        assertTrue(DrawdownService.ontarioTax(200001)>=DrawdownService.ontarioTax(200000));
    }
    @Test void chatbotChangesAreBoundedAndUseCalculatorValidation() {
        var p=plan(30,65,95,"10000","500","3500");
        var updated=ChatController.apply(p,List.of(new AiGateway.Change("monthlyContribution",750)));
        assertEquals(d("750.0"),updated.savings().monthlyContribution());assertEquals(65,updated.savings().retirementAge());
        assertThrows(IllegalArgumentException.class,()->ChatController.apply(p,List.of(new AiGateway.Change("retirementAge",64.5))));
        assertThrows(IllegalArgumentException.class,()->ChatController.apply(p,List.of(new AiGateway.Change("province",1))));
        assertThrows(IllegalArgumentException.class,()->retirement.project(ChatController.apply(p,List.of(new AiGateway.Change("monthlySpending",-1)))));
    }
}
