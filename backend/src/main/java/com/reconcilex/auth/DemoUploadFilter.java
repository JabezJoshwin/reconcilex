package com.reconcilex.auth;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance demo protection, not a distributed rate limiter. */
public final class DemoUploadFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final Admission admission;
    public DemoUploadFilter(boolean enabled) { this(enabled,new Admission(Clock.systemUTC())); }
    DemoUploadFilter(boolean enabled,Admission admission) { this.enabled=enabled; this.admission=admission; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if(!enabled || !req.getMethod().equals("POST") || !req.getRequestURI().equals("/api/reconciliations")) { chain.doFilter(req,res); return; }
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated()) { chain.doFilter(req,res); return; }
        int status=admission.enter(auth.getName());
        if(status!=200) {
            res.setHeader("Retry-After",status==429?"60":"5");
            SecurityConfiguration.json(res,status,status==429?"RATE_LIMITED":"BUSY",status==429?"Demo limit reached; retry in one minute":"Demo is busy; retry shortly");
            return;
        }
        try { chain.doFilter(req,res); } finally { admission.leave(); }
    }
    static final class Admission {
        private record Window(long start,int count) {}
        private final HashMap<String,Window> users=new HashMap<>();
        private final Semaphore slots=new Semaphore(2);
        private final Clock clock;
        Admission(Clock clock) { this.clock=clock; }
        synchronized int enter(String owner) {
            if(!slots.tryAcquire()) return 503;
            long now=clock.millis();
            users.entrySet().removeIf(e -> now-e.getValue().start()>=60000);
            var window=users.get(owner);
            if(window==null && users.size()>=2000) { slots.release(); return 503; }
            if(window!=null && window.count()>=10) { slots.release(); return 429; }
            users.put(owner,new Window(window==null?now:window.start(),window==null?1:window.count()+1));
            return 200;
        }
        void leave() { slots.release(); }
    }
}
