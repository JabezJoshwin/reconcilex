package com.reconcilex.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ActiveProfiles("production")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:production;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.base-url=https://reconcilex.example","app.clerk.publishable-key=pk_live_Y2xlcmsucmVjb25jaWxleC5leGFtcGxlJA","app.clerk.issuer=https://clerk.reconcilex.example"})
@AutoConfigureMockMvc
class ProductionCookieTest {
    @Autowired MockMvc mvc;
    @Test void productionCookieIsSecureEvenIfBackendConnectionIsHttp() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
            .andExpect(cookie().secure("SESSION",true)).andExpect(cookie().httpOnly("SESSION",true))
            .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("SameSite=Lax"),org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Domain=")))));
        mvc.perform(get("/api/auth/csrf").secure(true)).andExpect(header().exists("Strict-Transport-Security"));
    }
}
