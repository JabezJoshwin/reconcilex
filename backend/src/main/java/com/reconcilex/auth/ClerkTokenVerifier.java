package com.reconcilex.auth;

import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class ClerkTokenVerifier {
    private final ClerkSettings settings;
    private final JwtDecoder decoder;
    @org.springframework.beans.factory.annotation.Autowired
    public ClerkTokenVerifier(ClerkSettings settings) {
        this.settings=settings;
        if(settings.configured()) {
            var nimbus=NimbusJwtDecoder.withJwkSetUri(settings.issuer()+"/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
            nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
            decoder=nimbus;
        } else decoder=null;
    }
    ClerkTokenVerifier(ClerkSettings settings, JwtDecoder decoder) { this.settings=settings; this.decoder=decoder; }
    public Jwt verify(String token) {
        if(decoder==null || token==null || token.length()>16384) throw new BadJwtException("Invalid session");
        Jwt jwt=decoder.decode(token);
        Instant now=Instant.now();
        if (!settings.issuer().equals(jwt.getClaimAsString("iss")) ||
            !settings.baseUrl().equals(jwt.getClaimAsString("azp")) ||
            jwt.getExpiresAt()==null || !jwt.getExpiresAt().isAfter(now) ||
            jwt.getNotBefore()==null || jwt.getNotBefore().isAfter(now) ||
            jwt.getIssuedAt()==null || jwt.getIssuedAt().isAfter(now) ||
            java.time.Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).getSeconds()>120 ||
            jwt.getSubject()==null || !jwt.getSubject().matches("user_[A-Za-z0-9]+") ||
            jwt.getClaimAsString("sid")==null || !jwt.getClaimAsString("sid").matches("sess_[A-Za-z0-9]+") ||
            "pending".equals(jwt.getClaimAsString("sts"))) throw new BadJwtException("Invalid session claims");
        return jwt;
    }
}
