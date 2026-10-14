package com.reconcilex.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:clerk;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
    "app.base-url=http://localhost:8080","app.clerk.issuer=https://fixture.clerk.accounts.dev","app.clerk.publishable-key=pk_test_Zml4dHVyZS5jbGVyay5hY2NvdW50cy5kZXYk"})
@AutoConfigureMockMvc
@Import(ClerkFlowTest.VerificationFixture.class)
class ClerkFlowTest {
    static final RSAKey key;
    static { try { key=new RSAKeyGenerator(2048).keyID("fixture").generate(); } catch(Exception e) {throw new ExceptionInInitializerError(e);} }
    @TestConfiguration static class VerificationFixture {
        @Bean @Primary ClerkTokenVerifier fixtureVerifier(ClerkSettings settings) throws Exception {
            var decoder=NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
            return new ClerkTokenVerifier(settings,decoder);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    String token(String subject, Map<String,Object> changes, RSAKey signer) throws Exception {
        Instant now=Instant.now();
        Map<String,Object> claims=new HashMap<>();
        claims.put("iss","https://fixture.clerk.accounts.dev"); claims.put("sub",subject);
        claims.put("azp","http://localhost:8080"); claims.put("sid","sess_fixture");
        claims.put("iat",Date.from(now.minusSeconds(2))); claims.put("nbf",Date.from(now.minusSeconds(2))); claims.put("exp",Date.from(now.plusSeconds(60)));
        claims.putAll(changes);
        var builder=new JWTClaimsSet.Builder(); claims.forEach(builder::claim);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture").build(),builder.build());
        jwt.sign(new RSASSASigner(signer)); return jwt.serialize();
    }
    String token(String subject) throws Exception { return token(subject,Map.of(),key); }
    @Test void signedClerkTokenUploadsReloadsHistoryAndExportsWithoutLosingPrecision() throws Exception {
        String alice=token("user_Alice"), bob=token("user_Bob");
        byte[] csv="transaction_id,amount_minor,currency\n=1+1,9223372036854775807,USD\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var created=mvc.perform(multipart("/api/reconciliations").file(new MockMultipartFile("internalFile",csv))
            .file(new MockMultipartFile("processorFile",csv)).header("Authorization","Bearer "+alice).with(csrf()))
            .andExpect(status().isCreated()).andExpect(jsonPath("summary.counts.MATCHED").value(1)).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/reconciliations").header("Authorization","Bearer "+alice)).andExpect(status().isOk()).andExpect(jsonPath("totalRuns").value(1));
        mvc.perform(get("/api/reconciliations/"+id).cookie(new Cookie("__session",alice))).andExpect(status().isOk());
        String export=mvc.perform(get("/api/reconciliations/"+id+"/export").header("Authorization","Bearer "+alice)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(export).contains("'=1+1","9223372036854775807");
        for(String suffix:List.of("","/export")) mvc.perform(get("/api/reconciliations/"+id+suffix).header("Authorization","Bearer "+bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reconciliations").header("Authorization","Bearer "+bob)).andExpect(jsonPath("totalRuns").value(0));
    }
    @Test void rejectsTamperingWrongIssuerOriginExpiredFutureAndPendingSessions() throws Exception {
        for(var change:List.of(Map.<String,Object>of("iss","https://evil.example"),Map.<String,Object>of("azp","https://evil.example"),
            Map.<String,Object>of("exp",Date.from(Instant.now().minusSeconds(1))),Map.<String,Object>of("nbf",Date.from(Instant.now().plusSeconds(90))),
            Map.<String,Object>of("sts","pending"),Map.<String,Object>of("sid",""),Map.<String,Object>of("sub","not-a-user"))) {
            mvc.perform(get("/api/me").header("Authorization","Bearer "+token("user_Valid",change,key))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token("user_Valid",Map.of(),new RSAKeyGenerator(2048).generate()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("__session",token("user_Valid"))).header("Authorization","Basic invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("__session",token("user_Valid")),new Cookie("__session",token("user_Another")))).andExpect(status().isUnauthorized());
    }
    @Test void csrfAndOriginProtectionApplyToValidClerkTokens() throws Exception {
        String token=token("user_Csrf");
        mvc.perform(multipart("/api/reconciliations").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").header("Authorization","Bearer "+token).with(csrf()).header("Origin","https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").header("Authorization","Bearer "+token).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
    @Test void oldSpringAuthenticatedSessionCannotBypassClerk() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("11111111-1111-4111-8111-111111111111",null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void expiredCookieDoesNotBlockLoginSdkConfigurationAndNoCredentialsLeak() throws Exception {
        var expired=new Cookie("__session",token("user_Expired",Map.of("exp",Date.from(Instant.now().minusSeconds(90))),key));
        mvc.perform(get("/login").cookie(expired)).andExpect(status().isOk());
        var config=mvc.perform(get("/api/auth/config").cookie(expired)).andExpect(status().isOk()).andExpect(jsonPath("clerkConfigured").value(true)).andReturn();
        assertThat(config.getResponse().getContentAsString()).doesNotContain("secret","sk_test","sk_live");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE subject='user_Expired'",Integer.class)).isZero();
    }
}
