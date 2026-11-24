package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.model.HeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class PolicyCrudApiTest extends AbstractPolicyApiTest {

    // ---------- percorsi felici ----------

    @Test
    void get_asAgent_returnsPage() throws Exception {
        mvc.perform(get("/api/policies").with(agent()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void get_withFilter_returnsOnlyMatching() throws Exception {
        mvc.perform(get("/api/policies?type=AUTO").with(agent()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void post_asAdmin_is201_withActiveStatus() throws Exception {
        mvc.perform(post("/api/policies").with(admin()).header(APP, "test")
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_POLICY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.policyNumber").value("POL-0777"));
    }

    // ---------- errori di dominio ----------

    @Test
    void get_unknownId_is404_withJsonBody() throws Exception {
        mvc.perform(get("/api/policies/999").with(agent()).header(APP, "test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void post_duplicateNumber_is409() throws Exception {
        String dup = NEW_POLICY.replace("POL-0777", "POL-0001");
        mvc.perform(post("/api/policies").with(admin()).header(APP, "test")
                        .contentType(MediaType.APPLICATION_JSON).content(dup))
                .andExpect(status().isConflict());
    }

    @Test
    void post_negativePremium_is400() throws Exception {
        String bad = NEW_POLICY.replace("399.90", "-5");
        mvc.perform(post("/api/policies").with(admin()).header(APP, "test")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_endBeforeStart_is400() throws Exception {
        String bad = NEW_POLICY.replace("2027-10-31", "2026-10-01");
        mvc.perform(post("/api/policies").with(admin()).header(APP, "test")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    // ---------- filtri custom ----------

    @Test
    void api_withoutClientAppHeader_is400() throws Exception {
        mvc.perform(get("/api/policies").with(agent()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void api_withoutToken_andWithoutHeader_is401_notBadRequest() throws Exception {
        // la sicurezza risponde prima del controllo sull'header
        mvc.perform(get("/api/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void response_alwaysCarriesRequestId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().exists(HeaderNames.REQUEST_ID));
    }

    @Test
    void requestId_isEchoed_whenValid_andReplaced_whenMalicious() throws Exception {
        mvc.perform(get("/actuator/health").header(HeaderNames.REQUEST_ID, "prova-12345678"))
                .andExpect(header().string(HeaderNames.REQUEST_ID, "prova-12345678"));

        mvc.perform(get("/actuator/health").header(HeaderNames.REQUEST_ID, "bad value\nforged log line"))
                .andExpect(header().string(HeaderNames.REQUEST_ID, not("bad value\nforged log line")));
    }

    // ---------- login ----------

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent\",\"password\":\"agent123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_wrongPassword_andUnknownUser_giveSameMessage() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent\",\"password\":\"sbagliata\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nessuno\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void get_withStartDateRange_filtersByIsoDates() throws Exception {
        mvc.perform(get("/api/policies?startFrom=2026-03-01&startTo=2026-06-30")
                        .with(agent()).header(APP, "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].policyNumber").value("POL-0002"));
    }

    @Test
    void get_withInvalidDate_is400() throws Exception {
        mvc.perform(get("/api/policies?startFrom=ieri").with(agent()).header(APP, "test"))
                .andExpect(status().isBadRequest());
    }
}
