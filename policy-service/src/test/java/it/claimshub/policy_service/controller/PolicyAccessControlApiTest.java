package it.claimshub.policy_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PolicyAccessControlApiTest extends AbstractPolicyApiTest {

    // ---------- 401 / 403 ----------

    @Test
    void get_withoutToken_is401_withJsonBody() throws Exception {
        mvc.perform(get("/api/policies").header(APP, "test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void get_withFakeToken_is401() throws Exception {
        mvc.perform(get("/api/policies").header(APP, "test")
                        .header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_asAgent_is403_withJsonBody() throws Exception {
        mvc.perform(post("/api/policies").with(agent()).header(APP, "test")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POLICY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

}