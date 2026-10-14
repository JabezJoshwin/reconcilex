package com.reconcilex.auth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProductionConfigurationTest {
    @Test void productionRejectsHttpMissingCredentialsAndUntrustedOrigins() {
        var env=new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("production");
        for(String url:java.util.List.of("http://example.com","https://example.com","https://example.com?redirect=evil"))
            assertThatThrownBy(() -> new ClerkSettings(url,"","",env)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ClerkSettings("https://example.com","pk_test_Y2xlcmsuZXhhbXBsZS5jb20k","https://clerk.example.com",env)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ClerkSettings("https://example.com","pk_live_Y2xlcmsuZXhhbXBsZS5jb20k","https://evil.example.com",env)).isInstanceOf(IllegalStateException.class);
    }
}
