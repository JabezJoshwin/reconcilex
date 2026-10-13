package com.reconcilex.api;
import org.junit.jupiter.api.Test;
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
@SpringBootTest
@AutoConfigureMockMvc
class PostgresWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void realPostgresUploadReloadExport() throws Exception {
        byte[] csv="transaction_id,amount_minor,currency\npay_001,50000,INR\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var result=mvc.perform(multipart("/api/reconciliations").file(new MockMultipartFile("internalFile","i.csv","text/csv",csv)).file(new MockMultipartFile("processorFile","p.csv","text/csv",csv)))
            .andExpect(status().isCreated()).andExpect(jsonPath("summary.counts.MATCHED").value(1)).andReturn();
        String id=mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/reconciliations/"+id)).andExpect(status().isOk()).andExpect(jsonPath("findings[0].internalAmountMinor").value("50000"));
        mvc.perform(get("/api/reconciliations/"+id+"/export")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("pay_001,MATCHED,50000,INR,50000,INR,1,1")));
    }
}
