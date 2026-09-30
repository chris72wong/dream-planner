package com.example.retirement_planner;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;
import org.springframework.web.bind.annotation.*;
import static com.example.retirement_planner.PlanMath.*;

/** Fixed calculator explanations: no provider calls, advice generation or plan mutation. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    public record Turn(String role,String content) { }
    public record Request(String message,RetirementPlanRequest plan,AccountRulesRequest accounts,List<Turn> history,Map<String,Object> document) {
        public Request(String message,RetirementPlanRequest plan,AccountRulesRequest accounts,List<Turn> history) { this(message,plan,accounts,history,null); }
    }
    public record Result(String answer,List<AiGateway.Change> changes,RetirementPlanResult preview) { }
    private final RetirementPlanService retirement;
    public ChatController(RetirementPlanService retirement) { this.retirement=retirement; }
    @GetMapping("/status") public Map<String,Object> status() {
        return Map.of("available",true,"provider","local","sendsPlanSummary",false,"mode","calculator-explanations");
    }
    @PostMapping public Result ask(@RequestBody Request request) {
        require(request,"question");
        if(request.message()==null||!List.of("summary","assumptions","accounts","scenarios").contains(request.message()))
            throw new IllegalArgumentException("Choose a calculator explanation: summary, assumptions, accounts or scenarios. Investment recommendations and free-form advice are not supported.");
        if(request.accounts()!=null||request.document()!=null||request.history()!=null&&!request.history().isEmpty())
            throw new IllegalArgumentException("Send only the topic and, for a summary, the retirement calculator inputs. Account history and conversation history are not needed.");
        String answer=switch(request.message()) {
            case "summary" -> summary(request.plan());
            case "assumptions" -> "The retirement calculator uses fixed returns and inflation chosen by you. Savings grow monthly, with contributions at month-end. Retirement income and spending follow the entered assumptions. Results are estimates, not predictions or recommended returns. The retirement summary is before tax and excludes fees, market volatility, benefit eligibility checks and coordinated household planning.";
            case "accounts" -> "TFSA, RRSP and FHSA checks estimate eligibility and contribution room separately from the projection. Account balances alone do not establish contribution room. Enter account history or verified room and confirm it against your records and CRA information. The current checks use September 29, 2026; they do not enforce contribution room in future projection years or recommend an account allocation.";
            case "scenarios" -> "A scenario changes inputs that you choose; it is not a recommendation. Use Edit plan or Quick adjustments to enter your own saving, spending or retirement age, or compare the labelled scenarios. Fixed-return comparisons do not predict market performance or determine whether a decision is suitable for you. Consult an appropriately qualified professional for advice about your circumstances.";
            default -> throw new IllegalArgumentException("Unsupported explanation.");
        };
        return new Result(answer,List.of(),null);
    }
    private String summary(RetirementPlanRequest plan) {
        var r=retirement.project(plan);
        var money=NumberFormat.getCurrencyInstance(Locale.CANADA);money.setMaximumFractionDigits(0);
        return "With your selected inputs, the calculator estimates "+money.format(r.sustainableMonthlySpending())
                +" per month in today's CAD before tax through age "+plan.planningAge()+". Your entered spending target is "
                +money.format(plan.monthlySpending())+" per month. Projected savings at age "+plan.savings().retirementAge()
                +" are "+money.format(r.savings().finalBalance())+" in future CAD. These figures describe this fixed-assumption calculation; they are not a recommendation to retire, spend or invest and are not guaranteed.";
    }
    static RetirementPlanRequest apply(RetirementPlanRequest p,List<AiGateway.Change> changes) {
        var s=p.savings(); var contribution=s.monthlyContribution(); var spending=p.monthlySpending(); int age=s.retirementAge();
        var seen=new HashSet<String>();
        for(var change:changes) {
            if(change==null||change.field()==null||!Double.isFinite(change.value())||!seen.add(change.field())) throw new IllegalArgumentException("Invalid proposed plan change.");
            switch(change.field()) {
                case "monthlyContribution" -> contribution=BigDecimal.valueOf(change.value());
                case "monthlySpending" -> spending=BigDecimal.valueOf(change.value());
                case "retirementAge" -> { if(change.value()!=Math.rint(change.value())||change.value()<0||change.value()>120) throw new IllegalArgumentException("Proposed retirement age must be a whole age."); age=(int)change.value(); }
                default -> throw new IllegalArgumentException("Unsupported proposed plan change.");
            }
        }
        return new RetirementPlanRequest(new ProjectionRequest(s.currentAge(),age,s.currentSavings(),contribution,s.annualReturnRate(),s.annualInflationRate()),
                p.planningAge(),spending,p.retirementReturnRate(),p.cppMonthlyAt65(),p.cppStartAge(),p.oasMonthlyAt65(),p.oasStartAge(),p.otherMonthlyIncome(),p.otherIncomeStartAge());
    }
}
