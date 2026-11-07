package ucr.ac.cr.medivault.security;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final ClientIpResolver ips;
    private final ProblemResponseWriter problems;

    public RateLimitingFilter(ClientIpResolver ips, ProblemResponseWriter problems) {
        this.ips = ips;
        this.problems = problems;
    }

    private Bucket newBucket(long limit) {
        return Bucket.builder().addLimit(b -> b.capacity(limit)
                .refillIntervally(limit, Duration.ofMinutes(1))).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        Bucket bucket = null;
        if ("/api/v1/auth/login".equals(path) && "POST".equals(request.getMethod())) {
            bucket = authBuckets.computeIfAbsent(ips.resolve(request), ip -> newBucket(5));
        } else if (path.startsWith("/api/v1/expedientes")) {
            bucket = apiBuckets.computeIfAbsent(ips.resolve(request), ip -> newBucket(30));
        }
        if (bucket != null) {
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999999999) / 1000000000);
                response.setHeader("Retry-After", Long.toString(seconds));
                problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                        "Limite superado. Espere " + seconds + " segundos.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
