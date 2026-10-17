package cm.cetic.mezesse.service;

import cm.cetic.mezesse.config.CeticProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Stockage des photos d'élèves : Backblaze B2 (API compatible S3) si configuré, sinon un dossier local
 * (développement ; par défaut celui de l'ancienne API, pour partager les mêmes photos).
 */
@Slf4j
@Service
public class StockageService {

  private static final Pattern REGION = Pattern.compile("s3\\.([a-z0-9-]+)\\.");

  private final S3Client client;
  private final String bucket;
  private final Path dossierLocal;

  public StockageService(CeticProperties proprietes) {
    CeticProperties.B2 b2 = proprietes.b2();
    if (b2 != null && b2.configure()) {
      Matcher m = REGION.matcher(b2.endpoint());
      this.client =
          S3Client.builder()
              .endpointOverride(URI.create(b2.endpoint()))
              .region(Region.of(m.find() ? m.group(1) : "us-west-004"))
              .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(b2.keyId(), b2.applicationKey())))
              // B2 n'accepte pas toutes les sommes de contrôle ajoutées par défaut par les SDK récents.
              .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
              .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
              .build();
      this.bucket = b2.bucket();
      this.dossierLocal = null;
    } else {
      this.client = null;
      this.bucket = null;
      this.dossierLocal = Path.of(proprietes.dossierPhotos() == null ? "uploads" : proprietes.dossierPhotos()).toAbsolutePath().normalize();
    }
  }

  public boolean distant() {
    return client != null;
  }

  public void deposer(String cle, byte[] contenu, String type) {
    if (client != null) {
      client.putObject(b -> b.bucket(bucket).key(cle).contentType(type), RequestBody.fromBytes(contenu));
      return;
    }
    try {
      Path fichier = cheminLocal(cle);
      Files.createDirectories(fichier.getParent());
      Files.write(fichier, contenu);
    } catch (IOException e) {
      throw new IllegalStateException("Enregistrement de la photo impossible", e);
    }
  }

  /** Contenu du fichier, ou null s'il est introuvable. */
  public byte[] lire(String cle) {
    try {
      if (client != null) return client.getObjectAsBytes(b -> b.bucket(bucket).key(cle)).asByteArray();
      Path fichier = cheminLocal(cle);
      return Files.isRegularFile(fichier) ? Files.readAllBytes(fichier) : null;
    } catch (Exception e) {
      log.warn("Photo illisible : {}", cle);
      return null;
    }
  }

  /** Supprime le fichier ; un fichier déjà absent n'est pas une erreur. */
  public void supprimer(String cle) {
    try {
      if (client != null) client.deleteObject(b -> b.bucket(bucket).key(cle));
      else Files.deleteIfExists(cheminLocal(cle));
    } catch (Exception e) {
      log.warn("Suppression de la photo impossible : {}", cle);
    }
  }

  private Path cheminLocal(String cle) {
    Path fichier = dossierLocal.resolve(cle).normalize();
    if (!fichier.startsWith(dossierLocal)) throw new IllegalArgumentException("Clé de fichier invalide");
    return fichier;
  }
}
