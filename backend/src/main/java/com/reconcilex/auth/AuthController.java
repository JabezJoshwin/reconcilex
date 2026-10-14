package com.reconcilex.auth;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
@Controller
public class AuthController {
    private final UserStore users;
    private final ClerkSettings settings;
    public AuthController(UserStore users, ClerkSettings settings) {
        this.users=users; this.settings=settings;
    }
    @GetMapping({"/login","/signup"}) String login() { return "forward:/login.html"; }
    @GetMapping("/dashboard") String dashboard() { return "forward:/dashboard.html"; }
    @GetMapping("/api/me") @ResponseBody UserIdentity me() { return users.require(CurrentUser.id()); }
    @GetMapping("/api/auth/config") @ResponseBody Map<String,Object> config() {
        return Map.of("clerkConfigured",settings.configured(),"publishableKey",settings.publishableKey(),"frontendApi",settings.issuer(),"demo",settings.demo());
    }
    @GetMapping("/api/auth/csrf") @ResponseBody Map<String,String> csrf(HttpServletRequest request) {
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
}
