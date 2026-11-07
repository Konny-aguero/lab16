package ucr.ac.cr.medivault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class ProblemResponseWriter {
    private final ObjectMapper mapper;

    public ProblemResponseWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", "about:blank", "title", status.getReasonPhrase(),
                "status", status.value(), "detail", detail, "instance", request.getRequestURI()));
    }
}
