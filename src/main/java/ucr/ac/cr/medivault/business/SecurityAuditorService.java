package ucr.ac.cr.medivault.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SecurityAuditorService {
    private final JdbcTemplate jdbc;

    public SecurityAuditorService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String username, String ip, String accion, String recurso, int estado) {
        jdbc.update("""
                INSERT INTO AUDIT_LOGS (username, ip_origen, accion, recurso_solicitado, codigo_estado)
                VALUES (?, ?, ?, ?, ?)
                """, limitar(username, 50), limitar(ip, 45), accion, limitar(recurso, 255), estado);
    }

    private String limitar(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }
}
