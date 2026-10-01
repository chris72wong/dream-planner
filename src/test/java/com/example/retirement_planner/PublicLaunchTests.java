package com.example.retirement_planner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("public")
class PublicLaunchTests {
    @Autowired MockMvc mvc;
    @Test void publicVisitorsCanOpenCalculator() throws Exception {
        mvc.perform(get("/index.html").with(r->{r.setRemoteAddr("192.0.2.5");r.setServerName("summit.vercel.app");return r;}))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }
    @Test void sharedStorageIsUnavailableForEveryOperation() throws Exception {
        mvc.perform(get("/api/scenarios")).andExpect(status().isNotFound());
        mvc.perform(post("/api/scenarios").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/scenarios/anything")).andExpect(status().isNotFound());
    }
    @Test void publicCalculatorRetainsSameOriginProtection() throws Exception {
        mvc.perform(post("/api/projections").header("Origin","https://attacker.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/projections").header("Sec-Fetch-Site","cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/index.html").header("Sec-Fetch-Site","cross-site"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/projections").with(r->{r.setRemoteAddr("192.0.2.5");r.setServerName("summit.vercel.app");r.setScheme("https");r.setServerPort(443);return r;})
                .header("Origin","https://summit.vercel.app").contentType("application/json")
                .content("{\"currentAge\":30,\"retirementAge\":65,\"currentSavings\":10000,\"monthlyContribution\":500,\"annualReturnRate\":0.05,\"annualInflationRate\":0.02}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
}
