package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.JournalConsole;
import cm.cetic.mezesse.repository.JournalConsoleRepository;
import cm.cetic.mezesse.security.LimiteConnexionFilter;
import cm.cetic.mezesse.util.Textes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Inscrit au journal chaque connexion et chaque action de la console technique. */
@Service
@RequiredArgsConstructor
public class JournalConsoleService {

  private final JournalConsoleRepository journal;

  public void noter(String adminEmail, String action, String cible, String detail) {
    JournalConsole j = new JournalConsole();
    j.setAdminEmail(adminEmail);
    j.setAction(action);
    j.setCible(cible);
    j.setDetail(Textes.couper(detail, 1_000));
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes r) {
      j.setAdresse(LimiteConnexionFilter.adresse(r.getRequest()));
    }
    journal.save(j);
  }
}
