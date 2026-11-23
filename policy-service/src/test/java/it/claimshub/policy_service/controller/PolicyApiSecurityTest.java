package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.FixedClockConfig;
import it.claimshub.policy_service.model.HeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(FixedClockConfig.class)
class PolicyApiSecurityTest {

    @Autowired
    MockMvc mvc;

    private static final String APP = HeaderNames.CLIENT_APP;

    private static final String NEW_POLICY = """
        {"policyNumber":"POL-0777","holderName":"Test Api","type":"AUTO",
         "premium":399.90,"startDate":"2026-11-01","endDate":"2027-10-31"}""";

    private RequestPostProcessor agent() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
    }

    private RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

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