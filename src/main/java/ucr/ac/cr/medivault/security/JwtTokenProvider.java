package ucr.ac.cr.medivault.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${medivault.security.jwt.secret}") String secret,
                            @Value("${medivault.security.jwt.expiration-ms}") long expirationMs) {
        key = secret.isBlank() ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder().subject(username).claim("role", role).issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public long getExpirationMs() { return expirationMs; }
}
