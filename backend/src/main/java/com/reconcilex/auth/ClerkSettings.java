package com.reconcilex.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Only operator-configured origins may be used for token verification or SDK loading. */
@Component
public class ClerkSettings {
    private final String baseUrl, publishableKey, issuer;
    public ClerkSettings(@Value("${app.base-url}") String baseUrl,
        @Value("${app.clerk.publishable-key:}") String key,
        @Value("${app.clerk.issuer:}") String issuer, Environment env) {
        this.baseUrl=baseUrl; this.publishableKey=key; this.issuer=issuer;
        boolean production=env.matchesProfiles("production");
        URI base=origin(baseUrl);
        if ((!production && !List.of("localhost","127.0.0.1").contains(base.getHost())) ||
            (production && !"https".equals(base.getScheme())))
            throw new IllegalStateException("APP_BASE_URL must be a local origin or a production HTTPS origin");
        if (!key.isBlank() || !issuer.isBlank()) {
            URI api=origin(issuer);
            if (!"https".equals(api.getScheme()) || !key.matches("pk_(test|live)_[A-Za-z0-9_-]+"))
                throw new IllegalStateException("Configure a Clerk publishable key and HTTPS issuer");
            String encoded=key.substring(key.indexOf('_',3)+1);
            String domain=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
            if (!domain.equals(api.getHost()+"$") || api.getPort()!=-1)
                throw new IllegalStateException("Clerk issuer must match the publishable key's Frontend API domain");
        }
        if (production && (!configured() || !key.startsWith("pk_live_")))
            throw new IllegalStateException("Production requires a live Clerk instance");
    }
    static URI origin(String value) {
        URI uri=URI.create(value);
        if(uri.getHost()==null || !List.of("http","https").contains(uri.getScheme()) || uri.getUserInfo()!=null ||
            uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())
            throw new IllegalStateException("Configure an origin without a path, query, or fragment");
        return uri;
    }
    public boolean configured() { return !publishableKey.isBlank() && !issuer.isBlank(); }
    public String baseUrl() { return baseUrl; }
    public String publishableKey() { return publishableKey; }
    public String issuer() { return issuer; }
    public String contentSecurityPolicy() {
        String api=configured()?" "+issuer:"";
        // Clerk UI uses inline styles. Scripts remain restricted to self and the exact instance.
        String protection=configured()?" https://challenges.cloudflare.com https://*.protect.clerk.com":"";
        return "default-src 'self'; script-src 'self'"+api+protection+"; connect-src 'self'"+api+(configured()?" https://*.protect.clerk.com:*":"")+
            "; style-src 'self'"+(configured()?" 'unsafe-inline'":"")+
            "; img-src 'self' data: https://img.clerk.com; worker-src 'self' blob:; frame-src 'self'"+protection+"; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
    }
}
