package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.AdminConsole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminConsoleRepository extends JpaRepository<AdminConsole, String> {

  Optional<AdminConsole> findByEmail(String email);
}
