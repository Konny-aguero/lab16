package ucr.ac.cr.medivault.data;

import org.springframework.data.jpa.repository.JpaRepository;
import ucr.ac.cr.medivault.domain.Usuario;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
}
