package com.reconcilex.auth;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.base-url=http://localhost:8080","spring.security.oauth2.client.registration.google.client-id=unconfigured","spring.security.oauth2.client.registration.google.client-secret=unconfigured"})
@AutoConfigureMockMvc
class AccountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired UserStore users;
    @Autowired ObjectMapper mapper;
    UserIdentity alice,bob;
    @BeforeEach void setup() {
        alice=users.provision("https://accounts.google.com","alice","alice@example.test","<img src=x>");
        bob=users.provision("https://accounts.google.com","bob","bob@example.test","Bob");
    }
    @Test void repeatedLoginUsesSubjectNotEmail() {
        assertThat(users.provision("https://accounts.google.com","alice","changed@example.test","Alice").id()).isEqualTo(alice.id());
        assertThat(users.provision("https://accounts.google.com","different-subject","alice@example.test","Other").id()).isNotEqualTo(alice.id());
    }
    @Test void concurrentFirstLoginsCreateOneIdentity() throws Exception {
        String subject=java.util.UUID.randomUUID().toString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks=java.util.stream.IntStream.range(0,8).mapToObj(i -> (java.util.concurrent.Callable<java.util.UUID>)() -> users.provision("https://accounts.google.com",subject,"race@example.test","Race").id()).toList();
            var ids=new java.util.HashSet<java.util.UUID>();
            for(var future:pool.invokeAll(tasks)) ids.add(future.get());
            assertThat(ids).hasSize(1);
        }
    }
    @Test void anonymousApiAndDashboardAreProtected() throws Exception {
        for(String path:new String[]{"/api/me","/api/reconciliations","/api/reconciliations/"+java.util.UUID.randomUUID(),"/api/reconciliations/"+java.util.UUID.randomUUID()+"/export"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHENTICATED"));
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/dashboard.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/login.html"));
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("clerkConfigured").value(false));
    }
    @Test void csrfRequiredEvenWithoutOriginAndLogoutRequiresPost() throws Exception {
        mvc.perform(multipart("/api/reconciliations").with(user(alice.getName()))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user(alice.getName()))).andExpect(status().isForbidden());
        mvc.perform(get("/logout").with(user(alice.getName()))).andExpect(status().isNotFound());
        mvc.perform(post("/logout").with(user(alice.getName())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty()).andExpect(jsonPath("headerName").value("X-CSRF-TOKEN"));
    }
    @Test void privateHistoryAndEveryReadRejectOtherOwners() throws Exception {
        var csv="transaction_id,amount_minor,currency\na,9223372036854775807,USD\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var result=mvc.perform(multipart("/api/reconciliations")
            .file(new MockMultipartFile("internalFile",csv)).file(new MockMultipartFile("processorFile",csv)).with(user(alice.getName())).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/reconciliations").with(user(alice.getName()))).andExpect(status().isOk()).andExpect(jsonPath("totalRuns").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/reconciliations").with(user(bob.getName()))).andExpect(status().isOk()).andExpect(jsonPath("totalRuns").value(0));
        for(String suffix:new String[]{"","?classification=MATCHED","/export"}) {
            mvc.perform(get("/api/reconciliations/"+id+suffix).with(user(bob.getName()))).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("NOT_FOUND"));
            mvc.perform(get("/api/reconciliations/"+id+suffix).with(user(alice.getName()))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/reconciliations").param("size","101").with(user(alice.getName()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").with(user(alice.getName()))).andExpect(jsonPath("displayName").value("<img src=x>"));
    }
    @Test void legacyUnownedRunsStayPrivateAndPaginationIsBounded() throws Exception {
        var id=java.util.UUID.randomUUID();
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate();
        // Use the real application datasource, not a separate fixture database.
        jdbc.setDataSource(dataSource);
        String summary="{\"internalRows\":0,\"processorRows\":0,\"distinctIds\":0,\"counts\":{}}";
        jdbc.update("INSERT INTO reconciliation_runs (id,created_at,status,internal_rows,processor_rows,summary_json) VALUES (?,CURRENT_TIMESTAMP,'COMPLETED',0,0,?)",id,summary);
        for(var owner:java.util.List.of(alice,bob))
            mvc.perform(get("/api/reconciliations/"+id).with(user(owner.getName()))).andExpect(status().isNotFound());
        for(String page:java.util.List.of("-1","2147483647"))
            mvc.perform(get("/api/reconciliations").param("page",page).param("size","100").with(user(alice.getName()))).andExpect(status().isBadRequest());
    }
    @Autowired javax.sql.DataSource dataSource;
}
