package com.reconcilex.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reconcilex;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=","app.base-url=http://localhost:8080","spring.security.oauth2.client.registration.google.client-id=unconfigured","spring.security.oauth2.client.registration.google.client-secret=unconfigured"})
@org.springframework.security.test.context.support.WithMockUser(username="11111111-1111-4111-8111-111111111111")
@AutoConfigureMockMvc
class ApiWorkflowTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate authJdbc;
    @org.junit.jupiter.api.BeforeEach void seedIdentity() {
        if(authJdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=?",Integer.class,java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"))==0)
            authJdbc.update("INSERT INTO app_users (id,issuer,subject,email,display_name,created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"),"https://accounts.google.com","test-owner","owner@example.test","Test owner");
    }
    static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder secureMultipart() {
        var request=multipart("/api/reconciliations"); request.with(csrf()); return request;
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.reconcilex.persistence.RunStore store;
    @Autowired com.reconcilex.service.ReconciliationService service;
    static final String HEADER="transaction_id,amount_minor,currency\n";
    MockMultipartFile file(String name,String csv) { return new MockMultipartFile(name,"input.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)); }
    JsonNode upload(String internal,String processor) throws Exception {
        var response=mvc.perform(secureMultipart().file(file("internalFile",internal)).file(file("processorFile",processor)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString());
    }
    @Test void persistedWorkflowAllCategoriesAndSafeExport() throws Exception {
        String internal=HEADER+"match,9223372036854775807,INR\namount,1,INR\ncurrency,1,INR\nboth,1,INR\nmissing,1,INR\ndupI,1,INR\ndupI,2,INR\ndupP,1,INR\ndupB,1,INR\ndupB,2,INR\n=1+1,10,INR\n";
        String processor=HEADER+"match,9223372036854775807,INR\namount,2,INR\ncurrency,1,USD\nboth,2,USD\nunexpected,1,INR\ndupI,1,INR\ndupP,1,INR\ndupP,2,INR\ndupB,1,INR\ndupB,2,INR\n=1+1,10,INR\n";
        var created=upload(internal,processor); String id=created.path("id").asText();
        assertThat(created.path("summary").path("counts").size()).isEqualTo(9);
        for (var c:com.reconcilex.domain.Classification.values()) assertThat(created.path("summary").path("counts").path(c.name()).asLong()).isEqualTo(c.name().equals("MATCHED")?2:1);
        var loaded=mapper.readTree(mvc.perform(get("/api/reconciliations/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(loaded.path("summary")).isEqualTo(created.path("summary"));
        assertThat(loaded.path("findings").size()).isEqualTo(10);
        for(var f:loaded.path("findings")) if(f.path("transactionId").asText().equals("match")) {
            assertThat(f.path("internalAmountMinor").isTextual()).isTrue();
            assertThat(f.path("internalAmountMinor").asText()).isEqualTo("9223372036854775807");
        }
        mvc.perform(get("/api/reconciliations/"+id).param("size","1").param("page","1")).andExpect(jsonPath("findings.length()").value(1));
        mvc.perform(get("/api/reconciliations/"+id).param("classification","DUPLICATE_BOTH")).andExpect(jsonPath("totalFindings").value(1)).andExpect(jsonPath("findings[0].internalAmountMinor").doesNotExist());
        String exported=mvc.perform(get("/api/reconciliations/"+id+"/export")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"reconcilex-"+id+".csv\""))
            .andReturn().getResponse().getContentAsString();
        assertThat(exported).contains("'=1+1,MATCHED").contains("9223372036854775807");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_source_rows WHERE run_id = ?",Integer.class,UUID.fromString(id))).isEqualTo(22);
        var again=upload(internal,processor);
        var againLoaded=mapper.readTree(mvc.perform(get("/api/reconciliations/"+again.path("id").asText())).andReturn().getResponse().getContentAsString());
        assertThat(againLoaded.path("findings")).isEqualTo(loaded.path("findings"));
    }
    @Test void invalidInputCreatesNoRunAndParametersAreBounded() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class);
        for(String row:new String[]{"x,0,INR", "x,9223372036854775808,INR", "x,1,INVALID", ",1,INR", "x,1.5,INR"})
            mvc.perform(secureMultipart().file(file("internalFile",HEADER+row)).file(file("processorFile",HEADER))).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_INPUT"));
        mvc.perform(secureMultipart().file(new MockMultipartFile("internalFile",new byte[5*1024*1024+1])).file(file("processorFile",HEADER))).andExpect(status().isBadRequest());
        mvc.perform(secureMultipart().file(file("internalFile",HEADER+"x,1,INR")).file(file("processorFile",HEADER+"x,-1,INR"))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class)).isEqualTo(before);
        mvc.perform(get("/api/reconciliations/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/reconciliations/nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reconciliations/"+UUID.randomUUID()).param("size","501")).andExpect(status().isBadRequest());
        mvc.perform(secureMultipart()).andExpect(status().isBadRequest());
    }
    @Test void headerOnlyAndHealthAndAssets() throws Exception {
        var run=upload(HEADER,HEADER);
        mvc.perform(get("/api/reconciliations/"+run.path("id").asText())).andExpect(jsonPath("findings.length()").value(0));
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("status").value("UP"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(header().exists("Content-Security-Policy"));
    }
    @Test void formulaPrefixesIncludingLeadingWhitespace() {
        for(String id:new String[]{"=1+1","+SUM(1)","-1","@SUM(1)","  =1"}) assertThat(ReconciliationController.safeCell(id)).startsWith("'");
    }
    @Test void frameworkClientErrorsPreserveTheirHttpStatusAndHeaders() throws Exception {
        mvc.perform(put("/api/reconciliations").with(csrf())).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow",org.hamcrest.Matchers.containsString("POST")));
        mvc.perform(post("/api/reconciliations").with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/not-a-route")).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("NOT_FOUND"));
        mvc.perform(get("/api/health").accept("text/csv")).andExpect(status().isNotAcceptable());
    }
    @Test void crossOriginUploadsAreRejectedBeforePersistence() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class);
        for(String origin:new String[]{"http://localhost:19090","https://evil.invalid","null","invalid-origin","http://localhost/path","http://user@localhost","http://localhost?x=1","http://localhost#x"})
            mvc.perform(secureMultipart().file(file("internalFile",HEADER)).file(file("processorFile",HEADER)).header("Origin",origin))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CROSS_ORIGIN_REJECTED"));
        for(String fetchSite:new String[]{"cross-site","same-site"})
            mvc.perform(secureMultipart().file(file("internalFile",HEADER)).file(file("processorFile",HEADER)).header("Sec-Fetch-Site",fetchSite))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class)).isEqualTo(before);
    }
    @Test void sameOriginBrowserAndNonBrowserUploadsStillWork() throws Exception {
        mvc.perform(secureMultipart().file(file("internalFile",HEADER)).file(file("processorFile",HEADER))
            .header("Origin","http://localhost:18080").header("Sec-Fetch-Site","same-origin")
            .with(request->{request.setServerName("localhost");request.setServerPort(18080);return request;})).andExpect(status().isCreated());
        mvc.perform(secureMultipart().file(file("internalFile",HEADER)).file(file("processorFile",HEADER)).header("Origin","http://localhost"))
            .andExpect(status().isCreated());
        upload(HEADER,HEADER);
    }
    @Test void malformedQuotingIsRejectedWithoutSilentlyChangingValues() throws Exception {
        for (String row : new String[]{"a\"b,1,INR", "a,1,\"INR\" ", "\"a\" tail,1,INR"})
            mvc.perform(secureMultipart().file(file("internalFile",HEADER+row)).file(file("processorFile",HEADER)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_INPUT"));
        upload(HEADER+"\"a\"\"b\",1,INR",HEADER+"\"a\"\"b\",1,INR");
    }
    @Test void databaseFailureRollsBackRunAndResults() {
        int runsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class);
        int resultsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_results",Integer.class);
        int sourcesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_source_rows",Integer.class);
        // Inject a database error after real inserts, through the actual service proxy.
        // This must fail if create() loses its transaction boundary.
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new org.springframework.dao.DataIntegrityViolationException("Injected failure after inserts");
        }).when(store).save(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyList());
        byte[] csv=(HEADER+"rollback,1,INR\n").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.create(new java.io.ByteArrayInputStream(csv),new java.io.ByteArrayInputStream(csv)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs",Integer.class)).isEqualTo(runsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_results",Integer.class)).isEqualTo(resultsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_source_rows",Integer.class)).isEqualTo(sourcesBefore);
    }
}
