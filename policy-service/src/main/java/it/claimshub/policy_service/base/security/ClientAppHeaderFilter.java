package it.claimshub.policy_service.base.security;

import it.claimshub.policy_service.model.HeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ClientAppHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String app = request.getHeader(HeaderNames.CLIENT_APP);
        if (app == null || app.isBlank()) {
            ErrorResponseWriter.write(response, HttpStatus.BAD_REQUEST,
                    "Missing required header " + HeaderNames.CLIENT_APP);
            return;
        }
        chain.doFilter(request, response);
    }
}