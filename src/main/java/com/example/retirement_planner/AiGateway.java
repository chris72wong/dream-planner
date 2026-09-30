package com.example.retirement_planner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiGateway {
    static final List<String> CHANGE_FIELDS=List.of("monthlyContribution","retirementAge","planningAge","monthlySpending",
            "tfsaBalance","rrspBalance","otherBalance","annualReturn","retirementReturn","inflation",
            "cppMonthlyAt65","cppStartAge","oasMonthlyAt65","oasStartAge","otherMonthlyIncome","otherIncomeStartAge",
            "monthlyTakeHome","monthlyExpenses","emergencyCash","emergencyMonths","debtBalance","debtApr","debtPayment",
            "fhsaBalance","homeCash","homeMonthly","homeTarget","homeYears","homeReturn");
    private final String key, model;
    private final JsonMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public AiGateway(@Value("${north.ai.key:}") String key, @Value("${north.ai.model:}") String model, JsonMapper mapper) {
        this.key=key; this.model=model; this.mapper=mapper;
    }
    public boolean available() { return !key.isBlank() && !model.isBlank(); }
    public record Change(String field, double value) { }
    public record Reply(String answer, List<Change> changes) { }
    public Reply answer(String instructions, List<Map<String,String>> messages) {
        if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Ask Summit needs OPENAI_API_KEY and OPENAI_MODEL configured on the server.");
        var changeSchema = Map.of("type","object","properties",Map.of(
                "field",Map.of("type","string","enum",CHANGE_FIELDS),
                "value",Map.of("type","number")),"required",List.of("field","value"),"additionalProperties",false);
        var schema = Map.of("type","object","properties",Map.of("answer",Map.of("type","string"),
                "changes",Map.of("type","array","items",changeSchema)),"required",List.of("answer","changes"),"additionalProperties",false);
        var body=Map.of("model",model,"store",false,"instructions",instructions,"input",messages,
                "max_output_tokens",1200,"text",Map.of("format",Map.of("type","json_schema","name","north_answer","strict",true,"schema",schema)));
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(45)).header("Authorization","Bearer "+key).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The AI provider could not answer. Check the server's model and API configuration, then try again.");
            var root=mapper.readTree(response.body());
            if (!"completed".equals(root.path("status").asText())) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The AI response was incomplete. Try a shorter question.");
            var output=new StringBuilder();
            for (var item:root.path("output")) for (var content:item.path("content")) {
                if ("refusal".equals(content.path("type").asText())) return new Reply("I can't help with that request. Try asking about your plan or a savings scenario.",List.of());
                if ("output_text".equals(content.path("type").asText())) output.append(content.path("text").asText());
            }
            return mapper.readValue(output.toString(),Reply.class);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The AI request was interrupted."); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The AI provider is unavailable or returned an unreadable response. Please try again."); }
    }
}
