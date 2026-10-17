package cm.cetic.mezesse.repository;

import java.math.BigDecimal;

/** Note réduite à ce qu'utilise le calcul des résultats. */
public record NoteCalcul(String inscriptionId, String matiereId, int sequence, BigDecimal valeur) {}
