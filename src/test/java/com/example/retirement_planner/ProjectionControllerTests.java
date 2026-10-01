package com.example.retirement_planner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectionControllerTests {
    @Autowired
    private MockMvc mvc;

    private static final String BASE_REQUEST = """
            {"currentAge":30,"retirementAge":65,"currentSavings":10000,
             "monthlyContribution":500,"annualReturnRate":0,"annualInflationRate":0}
            """;

    @Test
    void returnsFullProjectionFromRealService() throws Exception {
        mvc.perform(post("/api/projections").contentType(MediaType.APPLICATION_JSON).content(BASE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.initialSavings").value(10000))
                .andExpect(jsonPath("$.totalContributions").value(210000))
                .andExpect(jsonPath("$.totalInvestmentGrowth").value(0))
                .andExpect(jsonPath("$.finalBalance").value(220000))
                .andExpect(jsonPath("$.inflationAdjustedFinalBalance").value(220000))
                .andExpect(jsonPath("$.annualBreakdown", hasSize(35)))
                .andExpect(jsonPath("$.annualBreakdown[0].projectionYear").value(1))
                .andExpect(jsonPath("$.annualBreakdown[0].ageAtYearEnd").value(31))
                .andExpect(jsonPath("$.annualBreakdown[34].endingBalance").value(220000));
    }

    @Test
    void acceptsDecimalStringsUsedByTheFormAndAdjustsForInflation() throws Exception {
        mvc.perform(post("/api/projections").contentType(MediaType.APPLICATION_JSON).content("""
                {"currentAge":60,"retirementAge":62,"currentSavings":"10000.00",
                 "monthlyContribution":"0.00","annualReturnRate":"0.05","annualInflationRate":"0.02"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalBalance").value(11025))
                .andExpect(jsonPath("$.inflationAdjustedFinalBalance").value(10596.89));
    }

    @ParameterizedTest
    @CsvSource({
            "currentSavings,-10,currentSavings must be non-negative",
            "monthlyContribution,-10,monthlyContribution must be non-negative",
            "annualReturnRate,-1,annualReturnRate must be greater than -1",
            "annualInflationRate,1.01,annualInflationRate must be greater than -1",
            "retirementAge,30,ages must satisfy",
            "retirementAge,121,ages must satisfy"
    })
    void exposesServiceValidationAsBadRequest(String field, String value, String message) throws Exception {
        String request = BASE_REQUEST.replaceFirst("\"" + field + "\":-?[0-9.]+", "\"" + field + "\":" + value);
        mvc.perform(post("/api/projections").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid projection inputs"))
                .andExpect(jsonPath("$.detail", containsString(message)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentAge", "retirementAge", "currentSavings", "monthlyContribution", "annualReturnRate", "annualInflationRate"})
    void rejectsMissingFields(String field) throws Exception {
        String request = BASE_REQUEST.replaceFirst("\"" + field + "\":[0-9.]+", "\"" + field + "\":null");
        mvc.perform(post("/api/projections").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(field + " is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{broken", "{\"currentAge\":\"hello\"}", "{\"currentAge\":30.5}"})
    void rejectsUnreadableBodiesAndFractionalAges(String body) throws Exception {
        mvc.perform(post("/api/projections").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid projection inputs"))
                .andExpect(jsonPath("$.detail", containsString("whole-number ages")));
    }

    @Test
    void servesCalculatorAndAssets() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("summit-welcome")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("view-overview"))));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void exportsExactAmountsAsAnAttachment() throws Exception {
        mvc.perform(post("/api/projections/csv")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("currentAge", "30").param("retirementAge", "65")
                        .param("currentSavings", "10000").param("monthlyContribution", "500")
                        .param("annualReturnRate", "0").param("annualInflationRate", "0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"retirement-projection.csv\""))
                .andExpect(content().string(containsString("1,31,6000.00,0.00,16000.00,16000.00\r\n")))
                .andExpect(content().string(containsString("35,65,6000.00,0.00,220000.00,220000.00\r\n")));
    }

    @Test
    void validatesExportInputs() throws Exception {
        mvc.perform(post("/api/projections/csv").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("currentAge is required"));
        mvc.perform(post("/api/projections/csv")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("currentAge", "30.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("whole-number ages")));
    }

    @Test
    void requiresJsonContentType() throws Exception {
        mvc.perform(post("/api/projections").contentType(MediaType.TEXT_PLAIN).content(BASE_REQUEST))
                .andExpect(status().isUnsupportedMediaType());
    }
}
