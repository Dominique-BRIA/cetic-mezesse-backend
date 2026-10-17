package cm.cetic.mezesse.service;

import cm.cetic.mezesse.repository.CompteurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matricule scolaire : préfixe de l'établissement + 2 chiffres de l'année d'entrée + rang à 4 chiffres,
 * par exemple CTM26-0007. Le compteur est incrémenté atomiquement par PostgreSQL.
 */
@Service
@RequiredArgsConstructor
public class MatriculeService {

  private final CompteurRepository compteurs;

  @Transactional
  public String nouveau(String prefixe, String libelleAnnee) {
    String aa = libelleAnnee.substring(2, 4);
    int rang = compteurs.incrementer("matricule-" + prefixe + "-" + aa);
    return prefixe + aa + "-" + String.format("%04d", rang);
  }
}
