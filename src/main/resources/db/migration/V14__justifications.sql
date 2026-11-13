-- Motifs de justification, réglables par le SG.
CREATE TABLE "MotifJustification" (
  "id" TEXT NOT NULL,
  "libelle" TEXT NOT NULL,
  "ordre" INTEGER NOT NULL DEFAULT 0,
  "actif" BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT "MotifJustification_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "MotifJustification_libelle_key" ON "MotifJustification"(lower("libelle"));
INSERT INTO "MotifJustification" ("id", "libelle", "ordre") VALUES
  ('motif-maladie', 'Maladie', 1),
  ('motif-rendez-vous-medical', 'Rendez-vous médical', 2),
  ('motif-autorisation-parentale', 'Autorisation parentale', 3),
  ('motif-raisons-familiales', 'Raisons familiales', 4),
  ('motif-autre', 'Autre motif', 5);

-- Justification acceptée d'une absence : une seule par absence, sans dossier commun. Plusieurs absences
-- justifiées ensemble reçoivent chacune la leur. Une justification refusée n'est pas enregistrée :
-- l'absence reste non justifiée.
CREATE TABLE "Justification" (
  "id" TEXT NOT NULL,
  "absenceId" TEXT NOT NULL,
  "motifId" TEXT NOT NULL,
  "description" TEXT,
  "dateReception" DATE NOT NULL,
  "parId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "modifieLe" TIMESTAMP(3),
  CONSTRAINT "Justification_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Justification_absenceId_fkey" FOREIGN KEY ("absenceId") REFERENCES "Absence"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Justification_motifId_fkey" FOREIGN KEY ("motifId") REFERENCES "MotifJustification"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "Justification_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Justification_absenceId_key" ON "Justification"("absenceId");

-- Pièces justificatives : le fichier est déposé une fois dans le stockage ; chaque justification qui
-- l'utilise a sa ligne (le fichier n'est retiré du stockage que lorsque plus aucune ligne n'y renvoie).
CREATE TABLE "PieceJustificative" (
  "id" TEXT NOT NULL,
  "justificationId" TEXT NOT NULL,
  "nomFichier" TEXT NOT NULL,
  "cle" TEXT NOT NULL,
  "typeMime" TEXT NOT NULL,
  "taille" INTEGER NOT NULL,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PieceJustificative_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "PieceJustificative_justificationId_fkey" FOREIGN KEY ("justificationId") REFERENCES "Justification"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "PieceJustificative_justificationId_idx" ON "PieceJustificative"("justificationId");
CREATE INDEX "PieceJustificative_cle_idx" ON "PieceJustificative"("cle");

-- L'historique suit aussi chaque absence (justifications, corrections).
ALTER TABLE "HistoriqueAbsence" ADD COLUMN "absenceId" TEXT;
CREATE INDEX "HistoriqueAbsence_absenceId_idx" ON "HistoriqueAbsence"("absenceId");

-- Recherche des absences par élève et par période.
CREATE INDEX "FicheAppel_date_idx" ON "FicheAppel"("date");
