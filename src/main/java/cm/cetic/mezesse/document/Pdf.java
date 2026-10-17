package cm.cetic.mezesse.document;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

/**
 * Petit outil de dessin PDF (PDFBox) avec des coordonnées « depuis le haut de la page », comme les
 * documents de l'ancienne API : x vers la droite, y vers le bas, en points (1/72 de pouce).
 */
public class Pdf implements AutoCloseable {

  public static final float LARGEUR = 595.28f;
  public static final float HAUTEUR = 841.89f;

  public enum Police {
    NORMALE(Standard14Fonts.FontName.HELVETICA, 718, 1.156f),
    GRASSE(Standard14Fonts.FontName.HELVETICA_BOLD, 718, 1.19f),
    ITALIQUE(Standard14Fonts.FontName.HELVETICA_OBLIQUE, 718, 1.156f);

    final PDType1Font fonte;
    final float ascendante;
    final float interligne;

    Police(Standard14Fonts.FontName nom, float ascendante, float interligne) {
      this.fonte = new PDType1Font(nom);
      this.ascendante = ascendante / 1000f;
      this.interligne = interligne;
    }
  }

  public enum Alignement {
    GAUCHE,
    CENTRE,
    DROITE
  }

  /** Style d'un texte : taille, police, couleur (#rrggbb), alignement dans sa largeur. */
  public record Style(float taille, Police police, String couleur, Alignement align) {
    public static Style de(float taille) {
      return new Style(taille, Police.NORMALE, Couleurs.ENCRE, Alignement.GAUCHE);
    }

    public Style gras() {
      return new Style(taille, Police.GRASSE, couleur, align);
    }

    public Style italique() {
      return new Style(taille, Police.ITALIQUE, couleur, align);
    }

    public Style couleur(String c) {
      return new Style(taille, police, c, align);
    }

    public Style centre() {
      return new Style(taille, police, couleur, Alignement.CENTRE);
    }

    public Style droite() {
      return new Style(taille, police, couleur, Alignement.DROITE);
    }

    public Style police(Police p) {
      return new Style(taille, p, couleur, align);
    }
  }

  private final PDDocument doc = new PDDocument();
  private final List<PDPage> pages = new ArrayList<>();
  private final Map<byte[], PDImageXObject> images = new IdentityHashMap<>();
  private PDPageContentStream cs;
  private PDPage page;

  public Pdf(String titre, String auteur) {
    PDDocumentInformation info = doc.getDocumentInformation();
    info.setTitle(titre);
    info.setAuthor(auteur);
    info.setCreator("CETIC de Mezesse, gestion scolaire");
  }

  public int nombrePages() {
    return pages.size();
  }

  public void nouvellePage() {
    fermerFlux();
    page = new PDPage(new PDRectangle(LARGEUR, HAUTEUR));
    doc.addPage(page);
    pages.add(page);
    cs = ouvrir(page, false);
  }

  /** Reprend une page déjà dessinée (pied de page « Page 1 / 3 »). */
  public void reprendrePage(int index) {
    fermerFlux();
    page = pages.get(index);
    cs = ouvrir(page, true);
  }

  private PDPageContentStream ouvrir(PDPage p, boolean ajout) {
    try {
      return new PDPageContentStream(doc, p, ajout ? PDPageContentStream.AppendMode.APPEND : PDPageContentStream.AppendMode.OVERWRITE, true, true);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void fermerFlux() {
    if (cs == null) return;
    try {
      cs.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    cs = null;
  }

  public byte[] enOctets() {
    fermerFlux();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    try {
      fermerFlux();
      doc.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---------- Texte ----------

  /** Les polices standard du PDF ne couvrent que Windows-1252 : les autres caractères sont remplacés. */
  public static String t(String s) {
    if (s == null) return "";
    StringBuilder b = new StringBuilder(s.length());
    for (char c : s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace('ᵉ', 'e').replace('ʳ', 'r').toCharArray()) {
      b.append(encodable(c) ? c : '?');
    }
    return b.toString();
  }

  private static final Map<Character, Boolean> ENCODABLES = new java.util.concurrent.ConcurrentHashMap<>();

  private static boolean encodable(char c) {
    return ENCODABLES.computeIfAbsent(
        c,
        k -> {
          try {
            Police.NORMALE.fonte.encode(String.valueOf(k));
            return true;
          } catch (Exception e) {
            return false;
          }
        });
  }

  public static float largeur(String s, Police police, float taille) {
    try {
      return police.fonte.getStringWidth(t(s)) / 1000f * taille;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Texte coupé avec « … » pour tenir dans la largeur. */
  static String ajuster(String s, Police police, float taille, float largeurMax) {
    String texte = t(s);
    if (largeur(texte, police, taille) <= largeurMax) return texte;
    String points = "…";
    int fin = texte.length();
    while (fin > 0 && largeur(texte.substring(0, fin) + points, police, taille) > largeurMax) fin--;
    return fin == 0 ? "" : texte.substring(0, fin).stripTrailing() + points;
  }

  /** Texte sur une seule ligne, coupé proprement s'il dépasse ; y = haut de la ligne. */
  public void ligne(String s, float x, float y, float largeurMax, Style style) {
    String texte = ajuster(s, style.police(), style.taille(), largeurMax);
    if (texte.isEmpty()) return;
    float l = largeur(texte, style.police(), style.taille());
    float dx = switch (style.align()) {
      case CENTRE -> (largeurMax - l) / 2;
      case DROITE -> largeurMax - l;
      default -> 0;
    };
    ecrire(texte, x + dx, y, style);
  }

  /** Écrit le texte sans le couper ; renvoie l'abscisse de fin (pour enchaîner plusieurs styles). */
  public float suite(String s, float x, float y, Style style) {
    String texte = t(s);
    ecrire(texte, x, y, style);
    return x + largeur(texte, style.police(), style.taille());
  }

  private void ecrire(String texte, float x, float y, Style style) {
    try {
      cs.beginText();
      cs.setFont(style.police().fonte, style.taille());
      cs.setNonStrokingColor(couleur(style.couleur()));
      cs.newLineAtOffset(x, HAUTEUR - y - style.police().ascendante * style.taille());
      cs.showText(texte);
      cs.endText();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Texte sur plusieurs lignes dans une boîte de hauteur fixe ; la dernière ligne visible finit par « … ». */
  public void bloc(String s, float x, float y, float largeurMax, float hauteurMax, Style style) {
    float hLigne = style.police().interligne * style.taille() - 0.5f;
    int maxLignes = Math.max(1, (int) Math.floor((hauteurMax + 0.5f) / hLigne));
    List<String> lignes = new ArrayList<>();
    for (String paragraphe : t(s).split("\n")) {
      StringBuilder courante = new StringBuilder();
      for (String mot : paragraphe.split(" ")) {
        String essai = courante.isEmpty() ? mot : courante + " " + mot;
        if (largeur(essai, style.police(), style.taille()) <= largeurMax || courante.isEmpty()) {
          courante.setLength(0);
          courante.append(essai);
        } else {
          lignes.add(courante.toString());
          courante.setLength(0);
          courante.append(mot);
        }
      }
      lignes.add(courante.toString());
    }
    for (int i = 0; i < Math.min(lignes.size(), maxLignes); i++) {
      String l = lignes.get(i);
      if (i == maxLignes - 1 && lignes.size() > maxLignes) l = l + " …";
      ligne(l, x, y + i * hLigne, largeurMax, style);
    }
  }

  // ---------- Formes ----------

  public static Color couleur(String hex) {
    return Color.decode(hex);
  }

  /** Rectangle au trait fin, rempli si un fond est donné. */
  public void cadre(float x, float y, float l, float h, String fond, String trait) {
    try {
      cs.saveGraphicsState();
      cs.setLineWidth(0.5f);
      cs.addRect(x, HAUTEUR - y - h, l, h);
      cs.setStrokingColor(couleur(trait));
      if (fond != null) {
        cs.setNonStrokingColor(couleur(fond));
        cs.fillAndStroke();
      } else cs.stroke();
      cs.restoreGraphicsState();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void cadre(float x, float y, float l, float h) {
    cadre(x, y, l, h, null, Couleurs.TRAIT);
  }

  public void cadre(float x, float y, float l, float h, String fond) {
    cadre(x, y, l, h, fond, Couleurs.TRAIT);
  }

  public void remplir(float x, float y, float l, float h, String fond) {
    try {
      cs.setNonStrokingColor(couleur(fond));
      cs.addRect(x, HAUTEUR - y - h, l, h);
      cs.fill();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void trait(float x1, float y1, float x2, float y2, float epaisseur, String couleur) {
    try {
      cs.saveGraphicsState();
      cs.setLineWidth(epaisseur);
      cs.setStrokingColor(couleur(couleur));
      cs.moveTo(x1, HAUTEUR - y1);
      cs.lineTo(x2, HAUTEUR - y2);
      cs.stroke();
      cs.restoreGraphicsState();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void cheminArrondi(float x, float y, float l, float h, float r) {
    try {
      float c = r * (1 - 0.5522847498f);
      float bas = HAUTEUR - y - h;
      float haut = HAUTEUR - y;
      cs.moveTo(x + r, haut);
      cs.lineTo(x + l - r, haut);
      cs.curveTo(x + l - c, haut, x + l, haut - c, x + l, haut - r);
      cs.lineTo(x + l, bas + r);
      cs.curveTo(x + l, bas + c, x + l - c, bas, x + l - r, bas);
      cs.lineTo(x + r, bas);
      cs.curveTo(x + c, bas, x, bas + c, x, bas + r);
      cs.lineTo(x, haut - r);
      cs.curveTo(x, haut - c, x + c, haut, x + r, haut);
      cs.closePath();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void contourArrondi(float x, float y, float l, float h, float r, float epaisseur, String couleur) {
    try {
      cs.saveGraphicsState();
      cs.setLineWidth(epaisseur);
      cs.setStrokingColor(couleur(couleur));
      cheminArrondi(x, y, l, h, r);
      cs.stroke();
      cs.restoreGraphicsState();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Limite les dessins suivants au rectangle arrondi, jusqu'à finDecoupe(). */
  public void decouperArrondi(float x, float y, float l, float h, float r) {
    try {
      cs.saveGraphicsState();
      cheminArrondi(x, y, l, h, r);
      cs.clip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void decouper(float x, float y, float l, float h) {
    try {
      cs.saveGraphicsState();
      cs.addRect(x, HAUTEUR - y - h, l, h);
      cs.clip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void finDecoupe() {
    try {
      cs.restoreGraphicsState();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---------- Images ----------

  private PDImageXObject image(byte[] contenu) throws IOException {
    PDImageXObject img = images.get(contenu);
    if (img == null) {
      img = PDImageXObject.createFromByteArray(doc, contenu, "image");
      images.put(contenu, img);
    }
    return img;
  }

  /** Image étirée dans le rectangle. */
  public void image(byte[] contenu, float x, float y, float l, float h) throws IOException {
    cs.drawImage(image(contenu), x, HAUTEUR - y - h, l, h);
  }

  /** Image entière, réduite pour tenir dans la boîte et centrée. */
  public void imageAjustee(byte[] contenu, float x, float y, float l, float h) throws IOException {
    PDImageXObject img = image(contenu);
    float echelle = Math.min(l / img.getWidth(), h / img.getHeight());
    float il = img.getWidth() * echelle;
    float ih = img.getHeight() * echelle;
    cs.drawImage(img, x + (l - il) / 2, HAUTEUR - y - (h - ih) / 2 - ih, il, ih);
  }

  /** Image qui couvre toute la boîte (rognée), centrée ; à utiliser dans une découpe. */
  public void imageCouvrante(byte[] contenu, float x, float y, float l, float h) throws IOException {
    PDImageXObject img = image(contenu);
    float echelle = Math.max(l / img.getWidth(), h / img.getHeight());
    float il = img.getWidth() * echelle;
    float ih = img.getHeight() * echelle;
    cs.drawImage(img, x + (l - il) / 2, HAUTEUR - y - (h - ih) / 2 - ih, il, ih);
  }

  /** Transparence des dessins suivants (1 = opaque). */
  public void opacite(float alpha) {
    try {
      PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
      gs.setNonStrokingAlphaConstant(alpha);
      gs.setStrokingAlphaConstant(alpha);
      cs.setGraphicsStateParameters(gs);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---------- Ressources ----------

  private static byte[] logo;

  /** Sceau du CETIC de Mezesse (JPEG 300 px). */
  public static synchronized byte[] logo() {
    if (logo == null) {
      try (InputStream in = Pdf.class.getResourceAsStream("/documents/logo.jpg")) {
        logo = in.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return logo;
  }

  /** Couleurs des documents. */
  public static final class Couleurs {
    private Couleurs() {}

    public static final String ENCRE = "#1a2230";
    public static final String GRIS = "#5b6676";
    public static final String TRAIT = "#6f7c8c";
    public static final String TRAIT_FIN = "#b9c3cf";
    public static final String ENTETE = "#dde7f1";
    public static final String GROUPE = "#eef3f8";
    public static final String ACCENT = "#1d4f91";
  }
}
