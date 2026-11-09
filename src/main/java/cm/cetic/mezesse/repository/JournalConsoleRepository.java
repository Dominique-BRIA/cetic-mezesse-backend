package cm.cetic.mezesse.repository;

import cm.cetic.mezesse.entity.JournalConsole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalConsoleRepository extends JpaRepository<JournalConsole, String> {

  List<JournalConsole> findTop200ByOrderByDateDesc();
}
