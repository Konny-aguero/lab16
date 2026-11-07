package ucr.ac.cr.medivault.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ucr.ac.cr.medivault.business.SecurityAuditorService;
import ucr.ac.cr.medivault.security.ClientIpResolver;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalSecurityExceptionHandler {
    private final SecurityAuditorService auditor;
    private final ClientIpResolver ips;

    public GlobalSecurityExceptionHandler(SecurityAuditorService auditor, ClientIpResolver ips) {
        this.auditor = auditor;
        this.ips = ips;
    }

    @ExceptionHandler(AccessDeniedSecurityException.class)
    public ProblemDetail accesoDenegado(AccessDeniedSecurityException ex, HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        auditor.registrar(auth == null ? null : auth.getName(), ips.resolve(request),
                "IDOR_ATTEMPT_BLOCKED", request.getRequestURI(), 403);
        return problem(HttpStatus.FORBIDDEN, "Acceso Prohibido (IDOR / BOLA)", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail credenciales(HttpServletRequest request) {
        auditor.registrar((String) request.getAttribute("loginUsername"), ips.resolve(request),
                "LOGIN_FAILED", request.getRequestURI(), 401);
        return problem(HttpStatus.UNAUTHORIZED, "Fallo de Autenticacion", "Credenciales invalidas.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail noEncontrado(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso No Encontrado", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException ex) {
        var result = problem(HttpStatus.BAD_REQUEST, "Error de Validacion", "Validacion de datos fallida.");
        var fields = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        result.setProperty("invalid_fields", fields);
        return result;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ProblemDetail solicitudInvalida() {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud Invalida", "Revise el formato y los datos enviados.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto() {
        return problem(HttpStatus.CONFLICT, "Conflicto", "Los datos entran en conflicto con un registro existente.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail errorInterno() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error Interno", "Error interno del servidor.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(title);
        return result;
    }
}
