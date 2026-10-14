package com.reconcilex.auth;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DemoUploadFilterTest {
    static class MutableClock extends Clock {
        long time=100000;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(time);}
    }
    @Test void perUserWindowExpiresWithoutDiscardingActiveUsers() {
        var clock=new MutableClock();var limit=new DemoUploadFilter.Admission(clock);
        for(int i=0;i<10;i++){assertThat(limit.enter("alice")).isEqualTo(200);limit.leave();}
        assertThat(limit.enter("alice")).isEqualTo(429);
        assertThat(limit.enter("bob")).isEqualTo(200);limit.leave();
        clock.time+=60000;assertThat(limit.enter("alice")).isEqualTo(200);limit.leave();
    }
    @Test void concurrencyAndBoundedCountersRecover() {
        var clock=new MutableClock();var limit=new DemoUploadFilter.Admission(clock);
        assertThat(limit.enter("a")).isEqualTo(200);assertThat(limit.enter("b")).isEqualTo(200);
        assertThat(limit.enter("c")).isEqualTo(503);limit.leave();limit.leave();
        for(int i=0;i<1998;i++){assertThat(limit.enter("user"+i)).isEqualTo(200);limit.leave();}
        assertThat(limit.enter("overflow")).isEqualTo(503);
        clock.time+=60000;assertThat(limit.enter("overflow")).isEqualTo(200);limit.leave();
    }
    @Test void thrownRequestsReleaseConcurrencySlots() throws Exception {
        var filter=new DemoUploadFilter(true);
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("owner",null,java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            for(int i=0;i<3;i++) {
                var req=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/reconciliations");
                assertThatThrownBy(() -> filter.doFilter(req,new org.springframework.mock.web.MockHttpServletResponse(),(a,b)->{throw new java.io.IOException("fixture");})).isInstanceOf(java.io.IOException.class);
            }
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
}
