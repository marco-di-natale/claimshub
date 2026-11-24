package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.FixedClockConfig;
import it.claimshub.policy_service.model.HeaderNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(FixedClockConfig.class)
abstract class AbstractPolicyApiTest {

    protected static final String APP = HeaderNames.CLIENT_APP;

    protected static final String NEW_POLICY = """
        {"policyNumber":"POL-0777","holderName":"Test Api","type":"AUTO",
         "premium":399.90,"startDate":"2026-11-01","endDate":"2027-10-31"}""";

    @Autowired
    protected MockMvc mvc;

    protected RequestPostProcessor agent() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
    }

    protected RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}