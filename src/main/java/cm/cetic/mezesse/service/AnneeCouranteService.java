package cm.cetic.mezesse.service;

import cm.cetic.mezesse.entity.AnneeScolaire;
import cm.cetic.mezesse.exception.ApiException;
import cm.cetic.mezesse.repository.AnneeScolaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Année sur laquelle porte une requête : celle demandée (?anneeId=) ou, à défaut, l'année active. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnneeCouranteService {

  private final AnneeScolaireRepository annees;

  public String anneeDe(String demandee) {
    if (demandee != null && !demandee.isBlank()) return demandee;
    return annees
        .findFirstByActiveTrue()
        .map(AnneeScolaire::getId)
        .orElseThrow(() -> ApiException.conflit("Aucune année scolaire active : le Surveillant Général doit en créer une."));
  }
}
