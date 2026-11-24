package it.claimshub.policy_service.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PolicyReportApiTest extends AbstractPolicyApiTest {

    @Test
    void reports_admin_gets200() throws Exception {
        mvc.perform(get("/api/reports/premium-ranking").with(admin()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    @Test
    void reports_agent_gets403() throws Exception {
        mvc.perform(get("/api/reports/premiums-by-type").with(agent()).header(APP, "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reports_withoutToken_gets401() throws Exception {
        mvc.perform(get("/api/reports/monthly-new-policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reports_invalidParameters_get400() throws Exception {
        mvc.perform(get("/api/reports/premiums-by-type?minPolicies=0")
                        .with(admin()).header(APP, "test"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/premiums-by-type?status=BOH")
                        .with(admin()).header(APP, "test"))
                .andExpect(status().isBadRequest());
    }
}
