-- Doublons : une fiche fusionnée dans une autre est gardée (désactivée, sans inscription) pour que la
-- fusion puisse être défaite ; elle indique la fiche qui l'a absorbée.
ALTER TABLE "Utilisateur" ADD COLUMN "fusionneDansId" TEXT;
ALTER TABLE "Utilisateur" ADD CONSTRAINT "Utilisateur_fusionneDansId_fkey"
  FOREIGN KEY ("fusionneDansId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE "Eleve" ADD COLUMN "fusionneDansId" TEXT;
ALTER TABLE "Eleve" ADD CONSTRAINT "Eleve_fusionneDansId_fkey"
  FOREIGN KEY ("fusionneDansId") REFERENCES "Eleve"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- Points de la page « À vérifier » que la direction a jugés normaux : ils ne sont plus signalés.
CREATE TABLE "VerificationIgnoree" (
  "cle" TEXT NOT NULL,
  "libelle" TEXT NOT NULL,
  "parId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "VerificationIgnoree_pkey" PRIMARY KEY ("cle"),
  CONSTRAINT "VerificationIgnoree_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
