package it.claimshub.policy_service.base.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ClientAppHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Client-App";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String app = request.getHeader(HEADER);
        if (app == null || app.isBlank()) {
            ErrorResponseWriter.write(response, HttpStatus.BAD_REQUEST,
                    "Missing required header " + HEADER);
            return;
        }
        chain.doFilter(request, response);
    }
}