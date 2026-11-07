package ucr.ac.cr.medivault.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${medivault.security.trusted-proxies:}") String proxies) {
        trustedProxies = Arrays.stream(proxies.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isTrusted(remote) || forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] addresses = forwarded.split(",");
        String current = remote;
        for (int i = addresses.length - 1; i >= 0 && isTrusted(current); i--) {
            String candidate = addresses[i].trim();
            if (!candidate.matches("[0-9a-fA-F:.]{2,45}")) {
                return remote;
            }
            try {
                new IpAddressMatcher(candidate);
            } catch (IllegalArgumentException ex) {
                return remote;
            }
            current = candidate;
        }
        return current;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }
}
