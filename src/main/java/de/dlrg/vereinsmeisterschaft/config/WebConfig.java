package de.dlrg.vereinsmeisterschaft.config;

import de.dlrg.vereinsmeisterschaft.web.RaceSocketHandler;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebConfig implements WebSocketConfigurer {
    private final RaceSocketHandler socket;
    public WebConfig(RaceSocketHandler socket) { this.socket=socket; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Gleiches Origin bleibt Pflicht; kein setAllowedOrigins("*").
        registry.addHandler(socket,"/ws").addInterceptors(new HttpSessionHandshakeInterceptor());
    }
    @Bean public OncePerRequestFilter localApiFilter() {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
                response.setHeader("X-Content-Type-Options","nosniff");
                response.setHeader("X-Frame-Options","DENY");
                response.setHeader("Referrer-Policy","no-referrer");
                response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
                if(request.getRequestURI().startsWith("/api/")) {
                    response.setHeader("Cache-Control","no-store");
                    String origin=request.getHeader("Origin");
                    if(origin!=null) {
                        try {
                            URI uri=URI.create(origin);
                            if(!request.getScheme().equals(uri.getScheme()) || !request.getHeader("Host").equalsIgnoreCase(uri.getRawAuthority())) {
                                response.sendError(403,"Fremder Ursprung nicht erlaubt."); return;
                            }
                        } catch(Exception ex) { response.sendError(403); return; }
                    }
                }
                chain.doFilter(request,response);
            }
        };
    }
}
