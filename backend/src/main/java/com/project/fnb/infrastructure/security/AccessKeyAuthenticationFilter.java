package com.project.fnb.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class AccessKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_KEY_HEADER = "X-Access-Key";

    private final AuthenticationManager authenticationManager;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessKey = request.getHeader(ACCESS_KEY_HEADER);

        if (StringUtils.hasText(accessKey)) {
            try {
                AccessKeyAuthenticationToken authRequest = new AccessKeyAuthenticationToken(accessKey);
                Authentication authResult = authenticationManager.authenticate(authRequest);
                SecurityContextHolder.getContext().setAuthentication(authResult);
            } catch (Exception e) {
                log.error("Lỗi xác thực Access Key", e);
                // Clear context for secure reasons
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"code\": 401, \"message\": \"Access Key không hợp lệ hoặc đã bị vô hiệu hoá\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
