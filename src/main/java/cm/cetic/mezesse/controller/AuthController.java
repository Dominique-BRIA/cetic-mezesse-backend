package cm.cetic.mezesse.controller;

import cm.cetic.mezesse.dto.Message;
import cm.cetic.mezesse.dto.UtilisateurDto.ChangementMotDePasse;
import cm.cetic.mezesse.dto.UtilisateurDto.Connexion;
import cm.cetic.mezesse.dto.UtilisateurDto.ModificationProfil;
import cm.cetic.mezesse.dto.UtilisateurDto.Profil;
import cm.cetic.mezesse.dto.UtilisateurDto.ReponseConnexion;
import cm.cetic.mezesse.security.UtilisateurConnecte;
import cm.cetic.mezesse.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService auth;

  @GetMapping("/sante")
  public Map<String, String> sante() {
    return Map.of("statut", "ok");
  }

  /** Public, limité à 20 tentatives par quart d'heure (LimiteConnexionFilter). */
  @PostMapping("/auth/connexion")
  public ReponseConnexion connexion(@Valid @RequestBody Connexion c) {
    return auth.connexion(c);
  }

  @GetMapping("/auth/moi")
  public Profil moi(@AuthenticationPrincipal UtilisateurConnecte moi) {
    return auth.moi(moi);
  }

  @PutMapping("/auth/moi")
  public Profil modifierProfil(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody ModificationProfil d) {
    return auth.modifierProfil(moi, d);
  }

  @PostMapping("/auth/mot-de-passe")
  public Message changerMotDePasse(@AuthenticationPrincipal UtilisateurConnecte moi, @Valid @RequestBody ChangementMotDePasse d) {
    auth.changerMotDePasse(moi, d);
    return new Message("Mot de passe modifié.");
  }
}
