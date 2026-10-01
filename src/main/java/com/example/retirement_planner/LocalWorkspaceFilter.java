package com.example.retirement_planner;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** This unauthenticated workspace is restricted to this computer and its own origin. */
@Component
public class LocalWorkspaceFilter extends OncePerRequestFilter {
    private static final Set<String> LOOPBACK=Set.of("127.0.0.1","::1","[::1]","0:0:0:0:0:0:0:1");
    private static boolean localHost(String host) { return "localhost".equalsIgnoreCase(host)||LOOPBACK.contains(host); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        if(request.getRequestURI().startsWith("/api/"))response.setHeader("Cache-Control","no-store");
        boolean allowed=LOOPBACK.contains(request.getRemoteAddr())&&localHost(request.getServerName());
        String origin=request.getHeader("Origin");
        if(origin!=null) {
            try {
                var uri=URI.create(origin);
                int port=uri.getPort()==-1?("https".equals(uri.getScheme())?443:80):uri.getPort();
                allowed=allowed&&request.getScheme().equals(uri.getScheme())
                        &&request.getServerName().equalsIgnoreCase(uri.getHost())&&request.getServerPort()==port;
            } catch(RuntimeException ex) { allowed=false; }
        }
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site")))allowed=false;
        if(!allowed) {
            response.setStatus(403);response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Local workspace only\",\"status\":403,\"detail\":\"Dream Planner has no public-user authentication. Access it from this computer using localhost or a loopback address.\"}");
            return;
        }
        chain.doFilter(request,response);
    }
}
