package it.claimshub.policy_service.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PolicyLifecycleApiTest extends AbstractPolicyApiTest {

    @Test
    void cancel_asAdmin_returnsCancelledPolicy() throws Exception {
        mvc.perform(patch("/api/policies/2/cancel").with(admin()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_twice_secondIs409() throws Exception {
        mvc.perform(patch("/api/policies/2/cancel").with(admin()).header(APP, "test"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/policies/2/cancel").with(admin()).header(APP, "test"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_asAgent_is403() throws Exception {
        mvc.perform(patch("/api/policies/2/cancel").with(agent()).header(APP, "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_withoutToken_is401() throws Exception {
        mvc.perform(patch("/api/policies/2/cancel").header(APP, "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_unknownPolicy_is404() throws Exception {
        mvc.perform(patch("/api/policies/999/cancel").with(admin()).header(APP, "test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void renew_activePolicy_extendsEndDate() throws Exception {
        mvc.perform(patch("/api/policies/1/renew").with(admin()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value("2027-12-31"));
    }

    @Test
    void renew_cancelledPolicy_is409() throws Exception {
        // POL-0004 è CANCELLED nei dati di prova
        mvc.perform(patch("/api/policies/4/renew").with(admin()).header(APP, "test"))
                .andExpect(status().isConflict());
    }

    @Test
    void renew_asAgent_is403() throws Exception {
        mvc.perform(patch("/api/policies/1/renew").with(agent()).header(APP, "test"))
                .andExpect(status().isForbidden());
    }
}
