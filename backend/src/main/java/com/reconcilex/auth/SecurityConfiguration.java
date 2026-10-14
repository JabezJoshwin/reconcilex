package com.reconcilex.auth;

import java.util.List;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http, ClerkSettings settings, ClerkTokenVerifier verifier, UserStore users) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/","/index.html","/login","/signup","/login.html","/api/health","/api/auth/config","/api/auth/csrf",
                "/assets/**","/vendor/**","/*.css","/presentation.js","/auth.js","/clerk.js","/error").permitAll()
            .anyRequest().authenticated())
            // Ignore legacy Google/JDBC authentication; Clerk tokens are verified on every request.
            .securityContext(c -> c.securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .requestCache(c -> c.disable())
            .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,error) -> {
                if(req.getRequestURI().startsWith("/api/")) json(res,401,"UNAUTHENTICATED","Sign in required");
                else res.sendRedirect("/login");
            }).accessDeniedHandler((req,res,error) -> json(res,403,"FORBIDDEN","Request not permitted; reload and retry")))
            .logout(l -> l.logoutUrl("/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("SESSION").logoutSuccessHandler((req,res,a) -> res.setStatus(204)))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(settings.contentSecurityPolicy()))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
                    if (!java.util.Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())) {
                        if (!com.reconcilex.api.SecurityHeaders.isSameOriginRequest(req)) {
                            json(res,403,"CROSS_ORIGIN_REJECTED","Cross-origin writes are not allowed"); return;
                        }
                    }
                    String token=null, header=req.getHeader("Authorization");
                    if(header!=null) {
                        if(!header.startsWith("Bearer ") || java.util.Collections.list(req.getHeaders("Authorization")).size()!=1) {
                            json(res,401,"UNAUTHENTICATED","Invalid session"); return;
                        }
                        token=header.substring(7);
                    } else if(req.getCookies()!=null) {
                        for(var cookie:req.getCookies()) if(cookie.getName().equals("__session")) {
                            if(token!=null) { json(res,401,"UNAUTHENTICATED","Invalid session"); return; }
                            token=cookie.getValue();
                        }
                    }
                    if(token!=null && !token.isBlank()) {
                        org.springframework.security.oauth2.jwt.Jwt jwt;
                        try { jwt=verifier.verify(token); }
                        catch(org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException invalid) {
                            // Login stays usable with an expired cookie so ClerkJS can refresh it.
                            if(req.getRequestURI().startsWith("/api/") && !req.getRequestURI().startsWith("/api/auth/") && !req.getRequestURI().equals("/api/health")) {
                                json(res,401,"UNAUTHENTICATED","Invalid or expired session"); return;
                            }
                            jwt=null;
                        }
                        if(jwt!=null) {
                            var user=users.provision(settings.issuer(),jwt.getSubject(),"","ReconcileX user");
                            var context=SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                            SecurityContextHolder.setContext(context);
                        }
                    }
                    if(req.getRequestURI().startsWith("/api/reconciliations") || req.getRequestURI().equals("/api/me")) {
                        var auth=SecurityContextHolder.getContext().getAuthentication();
                        if(auth==null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                            json(res,401,"UNAUTHENTICATED","Sign in required"); return;
                        }
                    }
                    chain.doFilter(req,res);
                }
            },CsrfFilter.class);
        return http.build();
    }
    static void json(HttpServletResponse res,int status,String code,String message) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
    }
}
