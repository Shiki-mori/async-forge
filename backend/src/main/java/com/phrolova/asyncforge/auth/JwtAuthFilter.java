package com.phrolova.asyncforge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phrolova.asyncforge.common.ErrorCode;
import com.phrolova.asyncforge.common.Result;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
@RequiredArgsConstructor
// OncePerRequestFilter 会标记请求，第二次遇到时跳过
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 若携带token，进入该分支

            // 去掉“Bearer ”这7个字符
            String token = authHeader.substring(7);

            try {
                // 取出userId。若token无效，getUserId抛出异常
                Long userId = jwtUtil.getUserId(token);
                // 将userId放入UserContext（业务代码使用）
                UserContext.setUserId(userId);
                // 将userId放入SecurityContextHolder（Spring Security使用）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // token无效，捕获异常
                writeUnauthorized(response);
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // UserContext 基于 ThreadLocal，Tomcat线程会复用
            // 如果不clear，下一个请求可能读到上一个用户的ID
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    // token无效时返回401
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.UNAUTHORIZED, "invalid token"));
    }
}
