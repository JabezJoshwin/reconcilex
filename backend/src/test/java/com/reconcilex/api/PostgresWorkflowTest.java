package com.reconcilex.api;
import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@EnabledIfEnvironmentVariable(named="RECONCILEX_POSTGRES_TEST",matches="true")
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"app.base-url=http://localhost:8080","spring.security.oauth2.client.registration.google.client-id=unconfigured","spring.security.oauth2.client.registration.google.client-secret=unconfigured"})
@org.springframework.security.test.context.support.WithMockUser(username="11111111-1111-4111-8111-111111111111")
@AutoConfigureMockMvc
class PostgresWorkflowTest {
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
    @Test void realPostgresUploadReloadExport() throws Exception {
        byte[] csv="transaction_id,amount_minor,currency\npay_001,50000,INR\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var result=mvc.perform(secureMultipart().file(new MockMultipartFile("internalFile","i.csv","text/csv",csv)).file(new MockMultipartFile("processorFile","p.csv","text/csv",csv)))
            .andExpect(status().isCreated()).andExpect(jsonPath("summary.counts.MATCHED").value(1)).andReturn();
        String id=mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/reconciliations/"+id)).andExpect(status().isOk()).andExpect(jsonPath("findings[0].internalAmountMinor").value("50000"));
        mvc.perform(get("/api/reconciliations/"+id+"/export")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("pay_001,MATCHED,50000,INR,50000,INR,1,1")));
    }
}
