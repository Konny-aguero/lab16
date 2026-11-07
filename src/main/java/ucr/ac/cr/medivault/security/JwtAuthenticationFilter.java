package ucr.ac.cr.medivault.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ucr.ac.cr.medivault.data.UsuarioRepository;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final UsuarioRepository usuarios;

    public JwtAuthenticationFilter(JwtTokenProvider tokens, UsuarioRepository usuarios) {
        this.tokens = tokens;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String username = tokens.getUsernameFromToken(header.substring(7));
                usuarios.findByUsername(username)
                        .filter(u -> "ACTIVO".equals(u.getEstado()) && !u.getCuentaBloqueada())
                        .ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(u.getUsername(), null,
                                        List.of(new SimpleGrantedAuthority(u.getRol().name())))));
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
