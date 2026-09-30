package com.example.retirement_planner;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScenarioRepository repository;
    @Autowired PlanDocumentValidator validator;
    @MockitoBean AiGateway ai;
    @BeforeEach void clear() { jdbc.update("DELETE FROM saved_scenarios"); }
    @SuppressWarnings("unchecked") Map<String,Object> document() throws Exception {
        try(var input=getClass().getResourceAsStream("/example-plan.json")) { return mapper.readValue(new String(input.readAllBytes(),StandardCharsets.UTF_8),Map.class); }
    }
    @Test void savesLoadsAndDeletesNamedScenarioThroughSqlAndHttp() throws Exception {
        var response=mvc.perform(post("/api/scenarios").contentType("application/json").content(mapper.writeValueAsString(Map.of("name","Chris's plan","document",document()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Chris's plan")).andReturn();
        String id=mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM saved_scenarios",Integer.class));
        mvc.perform(get("/api/scenarios")).andExpect(status().isOk()).andExpect(jsonPath("$[0].document.plan.monthlyContribution").value(500));
        // A new repository instance reads the same persisted rows; no in-memory library cache.
        assertEquals(id,new ScenarioRepository(jdbc,mapper,validator).list().getFirst().id());
        mvc.perform(delete("/api/scenarios/"+id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/scenarios/"+id)).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidAndIncompleteDocuments() throws Exception {
        mvc.perform(post("/api/scenarios").contentType("application/json").content("{\"name\":\"X\",\"document\":{}}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        var doc=document();var plan=new HashMap<Object,Object>((Map<?,?>)doc.get("plan"));plan.put("monthlyContribution",-1);doc.put("plan",plan);
        assertThrows(IllegalArgumentException.class,()->repository.save(new ScenarioRepository.SaveRequest("Invalid",doc)));
        assertTrue(repository.list().isEmpty());
    }
    @Test void sqlParametersDoNotExecuteScenarioNameAsSql() throws Exception {
        repository.save(new ScenarioRepository.SaveRequest("'); DROP TABLE saved_scenarios;--",document()));
        assertEquals(1,repository.list().size());
    }
    @Test void aiUnavailableIsExplicitAndDoesNotChangePlan() throws Exception {
        mvc.perform(get("/api/chat/status")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }
    @Test void chatbotRecomputesPreviewWithRealCalculator() throws Exception {
        when(ai.answer(anyString(),anyList())).thenReturn(new AiGateway.Reply("Here is the requested savings preview.",List.of(new AiGateway.Change("monthlyContribution",750))));
        var p=AnalysisTests.plan(30,65,95,"10000","500","3500");
        mvc.perform(post("/api/chat").contentType("application/json").content(mapper.writeValueAsString(new ChatController.Request("Save 250 more",p,null,List.of()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes[0].value").value(750))
                .andExpect(jsonPath("$.preview.savings.totalContributions").value(315000));
        assertEquals(AnalysisTests.d("500"),p.savings().monthlyContribution());
    }
    @Test void invalidChatPlanIsRejectedBeforeProviderCall() throws Exception {
        var p=AnalysisTests.plan(30,65,95,"10000","-1","3500");
        mvc.perform(post("/api/chat").contentType("application/json").content(mapper.writeValueAsString(new ChatController.Request("Explain",p,null,List.of()))))
                .andExpect(status().isBadRequest());verifyNoInteractions(ai);
    }
    @Test void chatCanPreviewAccountBalancesFromValidatedDocument() throws Exception {
        when(ai.answer(anyString(),anyList())).thenReturn(new AiGateway.Reply("I have prepared that balance change.",List.of(new AiGateway.Change("rrspBalance",50000))));
        var doc=document();var p=validator.validate(doc);
        mvc.perform(post("/api/chat").contentType("application/json").content(mapper.writeValueAsString(new ChatController.Request("Set my RRSP to 50000",p,null,List.of(),doc))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preview.savings.initialSavings").value(60000));
        assertEquals(0,((Number)((Map<?,?>)doc.get("plan")).get("rrspBalance")).intValue());
    }
    @Test void missingProviderConfigurationReturnsUnavailableWithoutNetworkCall() {
        var gateway=new AiGateway("","",mapper);assertFalse(gateway.available());
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->gateway.answer("Explain",List.of()));
        assertEquals(503,error.getStatusCode().value());
    }
    @Test void independentHouseholdPlansAreAddedInTodayDollars() throws Exception {
        var p=AnalysisTests.plan(64,65,67,"10000","0","1000");
        mvc.perform(post("/api/analysis/household").contentType("application/json").content(mapper.writeValueAsString(new AnalysisController.HouseholdRequest(p,p))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.combinedSupportedMonthlySpending").value(833.32));
    }
    @Test void servesNewUiAssets() throws Exception {
        mvc.perform(get("/workspace.js")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("chat-form")));
        mvc.perform(get("/workspace.css")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Start with a question.")));
    }
}


