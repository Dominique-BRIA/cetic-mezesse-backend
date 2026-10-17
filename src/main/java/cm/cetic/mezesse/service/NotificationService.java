package cm.cetic.mezesse.service;

import cm.cetic.mezesse.dto.NotificationDto;
import cm.cetic.mezesse.entity.Notification;
import cm.cetic.mezesse.entity.Sequence;
import cm.cetic.mezesse.repository.NotificationRepository;
import cm.cetic.mezesse.repository.UtilisateurRepository;
import cm.cetic.mezesse.util.Textes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notifications affichées dans l'application (RG-32). */
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notifications;
  private final UtilisateurRepository utilisateurs;

  @Transactional
  public void notifier(Collection<String> destinataireIds, String titre, String message, String lien) {
    List<Notification> nouvelles =
        new LinkedHashSet<>(destinataireIds)
            .stream()
                .map(
                    id -> {
                      Notification n = new Notification();
                      n.setDestinataire(utilisateurs.getReferenceById(id));
                      n.setTitre(titre);
                      n.setMessage(Textes.couper(message, 1000));
                      n.setLien(lien);
                      return n;
                    })
                .toList();
    notifications.saveAll(nouvelles);
  }

  /** Séquence ouverte à la saisie : chaque professeur actif qui a une matière cette année-là est prévenu. */
  @Transactional
  public void notifierOuvertureSequence(Sequence s) {
    notifier(
        utilisateurs.professeursActifsDeLAnnee(s.getAnneeId()),
        "Séquence " + s.getNumero() + " ouverte",
        "La saisie des notes de la séquence " + s.getNumero() + " (" + s.getAnnee().getLibelle()
            + ") est ouverte. Vous pouvez saisir les notes de vos matières.",
        "/saisie");
  }

  @Transactional(readOnly = true)
  public NotificationDto.Liste listeDe(String utilisateurId) {
    return new NotificationDto.Liste(
        notifications.findTop30ByDestinataire_IdOrderByCreeLeDesc(utilisateurId).stream().map(NotificationDto.Vue::de).toList(),
        notifications.countByDestinataire_IdAndLueFalse(utilisateurId));
  }

  @Transactional
  public void toutMarquerLu(String utilisateurId) {
    notifications.toutMarquerLu(utilisateurId);
  }

  @Transactional
  public void marquerLue(String id, String utilisateurId) {
    notifications.marquerLue(id, utilisateurId);
  }
}
