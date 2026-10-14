package com.reconcilex.api;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component
public class SecurityHeaders extends OncePerRequestFilter {
    private final com.reconcilex.auth.ClerkSettings settings;
    public SecurityHeaders(com.reconcilex.auth.ClerkSettings settings) { this.settings=settings; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Content-Security-Policy",settings.contentSecurityPolicy());
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("Cache-Control","no-store");
        if (isUnsafe(request.getMethod()) && !isSameOriginRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"CROSS_ORIGIN_REJECTED\",\"message\":\"Cross-origin writes are not allowed\"}");
            return;
        }
        chain.doFilter(request,response);
    }
    private boolean isUnsafe(String method) {
        return !java.util.Set.of("GET","HEAD","OPTIONS","TRACE").contains(method);
    }
    public static boolean isSameOriginRequest(HttpServletRequest request) {
        String fetchSite=request.getHeader("Sec-Fetch-Site");
        if(fetchSite!=null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) return false;
        String origin=request.getHeader("Origin");
        if(origin==null) return true; // Non-browser clients remain supported; this is not authentication.
        if(java.util.Collections.list(request.getHeaders("Origin")).size()!=1) return false;
        try {
            var uri=java.net.URI.create(origin);
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !uri.getRawPath().isEmpty()) return false;
            int port=uri.getPort()==-1 ? ("https".equalsIgnoreCase(uri.getScheme())?443:80) : uri.getPort();
            return request.getScheme().equalsIgnoreCase(uri.getScheme()) && request.getServerName().equalsIgnoreCase(uri.getHost()) && request.getServerPort()==port;
        } catch(IllegalArgumentException e) { return false; }
    }
}
