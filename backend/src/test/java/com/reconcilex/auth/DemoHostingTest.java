package com.reconcilex.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
@org.springframework.test.context.ActiveProfiles("demo")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
    "app.base-url=https://fixture.onrender.com","app.revision=fixture-sha","app.clerk.issuer=https://fixture.clerk.accounts.dev","app.clerk.publishable-key=pk_test_Zml4dHVyZS5jbGVyay5hY2NvdW50cy5kZXYk"})
@AutoConfigureMockMvc
class DemoHostingTest {
    @Autowired MockMvc mvc;
    @Autowired UserStore users;
    @Test void demoConfigurationRequiresHttpsAndCannotWeakenProduction() {
        var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("demo");
        assertThatThrownBy(()->new ClerkSettings("http://example.com","","",env)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new ClerkSettings("https://example.com","","",env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("demo","production");
        assertThatThrownBy(()->new ClerkSettings("https://example.com","pk_test_Zml4dHVyZS5jbGVyay5hY2NvdW50cy5kZXYk","https://fixture.clerk.accounts.dev",env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void cookieOriginAndRedirectUseConfiguredHttpsNotForwardedHeaders() throws Exception {
        mvc.perform(get("/dashboard").header("X-Forwarded-Host","evil.example").header("X-Forwarded-Proto","http"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("https://fixture.onrender.com/login"));
        mvc.perform(get("/api/auth/csrf")).andExpect(cookie().secure("SESSION",true)).andExpect(cookie().httpOnly("SESSION",true)).andExpect(header().exists("Strict-Transport-Security"));
        mvc.perform(post("/logout").with(user("fixture")).with(csrf()).header("Origin","https://fixture.onrender.com")).andExpect(status().isNoContent());
        mvc.perform(post("/logout").with(user("fixture")).with(csrf()).header("Origin","https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("demo").value(true));
    }
    @Test void readinessAndRevisionArePublicAndDoNotExposeSecrets() throws Exception {
        mvc.perform(get("/api/health")).andExpect(jsonPath("status").value("UP")).andExpect(jsonPath("revision").value("fixture-sha"));
        mvc.perform(get("/api/ready")).andExpect(status().isOk()).andExpect(jsonPath("status").value("UP"));
        var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.query(org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.ResultSetExtractor.class)))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret database address"));
        var response=new com.reconcilex.api.HealthController(jdbc,"fixture").ready();
        assertThat(response.getStatusCode().value()).isEqualTo(503);assertThat(response.getBody()).containsEntry("status","DOWN");
        assertThat(response.getBody().toString()).doesNotContain("secret");
    }
    @Test void demoRateLimitAppliesAtHttpBoundaryAndAnonymousUploadsStayRejected() throws Exception {
        var owner=users.provision("https://fixture.clerk.accounts.dev","user_RateFixture","","Fixture");
        byte[] csv="transaction_id,amount_minor,currency\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(int i=0;i<10;i++) mvc.perform(multipart("/api/reconciliations")
            .file(new org.springframework.mock.web.MockMultipartFile("internalFile",csv)).file(new org.springframework.mock.web.MockMultipartFile("processorFile",csv))
            .with(user(owner.getName())).with(csrf()).header("Origin","https://fixture.onrender.com")).andExpect(status().isCreated());
        mvc.perform(multipart("/api/reconciliations").with(user(owner.getName())).with(csrf()))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60")).andExpect(jsonPath("code").value("RATE_LIMITED"));
        mvc.perform(multipart("/api/reconciliations")).andExpect(status().isUnauthorized());
    }
}
