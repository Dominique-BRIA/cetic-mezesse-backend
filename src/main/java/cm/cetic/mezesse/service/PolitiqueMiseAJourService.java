package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.AppareilDto.Politique;
import cm.cetic.mezesse.dto.ConsoleDto.ReglagePolitique;
import cm.cetic.mezesse.dto.ConsoleDto.VuePolitique;
import cm.cetic.mezesse.entity.PolitiqueMiseAJour;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.PolitiqueMiseAJourRepository;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.util.Horloge;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Suspendre les mises à jour ou bloquer une version sans rien publier ; lu par l'application mobile. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolitiqueMiseAJourService {

  private final PolitiqueMiseAJourRepository politiques;
  private final JournalConsoleService journal;

  public Politique lire() {
    return vue(ligne());
  }

  public VuePolitique detail() {
    PolitiqueMiseAJour p = ligne();
    return new VuePolitique(vue(p), p.getMajLe(), p.getMajPar());
  }

  @Transactional
  public VuePolitique modifier(UtilisateurConnecte moi, ReglagePolitique d) {
    PolitiqueMiseAJour p = ligne();
    Politique avant = vue(p);
    p.setSuspendue(d.suspendue());
    p.setVersionsBloquees(d.versionsBloquees().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));
    p.setMinimum(d.minimum());
    p.setMessage(d.message());
    p.setMajLe(Horloge.maintenant());
    p.setMajPar(moi.nom());
    journal.noter(moi.nom(), "mises-a-jour.politique", null, "avant " + avant + " ; après " + vue(p));
    return new VuePolitique(vue(p), p.getMajLe(), p.getMajPar());
  }

  private PolitiqueMiseAJour ligne() {
    return politiques.findById(PolitiqueMiseAJour.ID).orElseThrow(() -> ApiException.introuvable("Politique de mise à jour"));
  }

  private static Politique vue(PolitiqueMiseAJour p) {
    List<Integer> bloquees = p.getVersionsBloquees().isBlank() ? List.of() : Arrays.stream(p.getVersionsBloquees().split(",")).map(Integer::valueOf).toList();
    return new Politique(p.isSuspendue(), bloquees, p.getMinimum(), p.getMessage());
  }
}
