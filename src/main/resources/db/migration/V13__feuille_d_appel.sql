-- Feuille d'appel : une par classe et par jour, rattachée à une séquence (par défaut la dernière ouverte).
-- Elle n'existe qu'une fois l'appel enregistré (la saisie en cours reste sur l'appareil du SG) ; toute
-- modification ensuite est une correction, historisée.
CREATE TABLE "FicheAppel" (
  "id" TEXT NOT NULL,
  "classeId" TEXT NOT NULL,
  "date" DATE NOT NULL,
  "sequenceId" TEXT NOT NULL,
  "creeParId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "modifieLe" TIMESTAMP(3),
  CONSTRAINT "FicheAppel_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "FicheAppel_classeId_fkey" FOREIGN KEY ("classeId") REFERENCES "Classe"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "FicheAppel_sequenceId_fkey" FOREIGN KEY ("sequenceId") REFERENCES "Sequence"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT "FicheAppel_creeParId_fkey" FOREIGN KEY ("creeParId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "FicheAppel_classe_date_key" ON "FicheAppel"("classeId", "date");
CREATE INDEX "FicheAppel_sequenceId_idx" ON "FicheAppel"("sequenceId");

-- Séances réellement programmées ce jour-là : tirées de l'emploi du temps en vigueur, puis ajustées
-- (horaire changé, remplaçant, séance annulée, séance ajoutée, enseignant absent). Le nom de la matière
-- est gardé pour que l'historique reste lisible si la matière est supprimée.
CREATE TABLE "Seance" (
  "id" TEXT NOT NULL,
  "ficheId" TEXT NOT NULL,
  "creneauId" TEXT,
  "matiereId" TEXT,
  "matiere" TEXT NOT NULL,
  "enseignantId" TEXT,
  "remplacantId" TEXT,
  "debut" TIME(0) NOT NULL,
  "fin" TIME(0) NOT NULL,
  "statut" TEXT NOT NULL DEFAULT 'PREVUE',
  "ajoutee" BOOLEAN NOT NULL DEFAULT false,
  "enseignantAbsent" BOOLEAN NOT NULL DEFAULT false,
  "motif" TEXT,
  CONSTRAINT "Seance_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Seance_ficheId_fkey" FOREIGN KEY ("ficheId") REFERENCES "FicheAppel"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Seance_creneauId_fkey" FOREIGN KEY ("creneauId") REFERENCES "Creneau"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "Seance_matiereId_fkey" FOREIGN KEY ("matiereId") REFERENCES "Matiere"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "Seance_enseignantId_fkey" FOREIGN KEY ("enseignantId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "Seance_remplacantId_fkey" FOREIGN KEY ("remplacantId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "Seance_statut_check" CHECK ("statut" IN ('PREVUE', 'ANNULEE')),
  CONSTRAINT "Seance_horaire_check" CHECK ("fin" > "debut")
);
CREATE INDEX "Seance_ficheId_idx" ON "Seance"("ficheId");
CREATE INDEX "Seance_matiereId_idx" ON "Seance"("matiereId");
CREATE INDEX "Seance_enseignantId_idx" ON "Seance"("enseignantId");

-- Une absence : un élève (son inscription, donc sa classe de l'année) à une séance. La présence n'est pas
-- enregistrée : tout élève sans absence est présent.
CREATE TABLE "Absence" (
  "id" TEXT NOT NULL,
  "seanceId" TEXT NOT NULL,
  "inscriptionId" TEXT NOT NULL,
  "parId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "Absence_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Absence_seanceId_fkey" FOREIGN KEY ("seanceId") REFERENCES "Seance"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Absence_inscriptionId_fkey" FOREIGN KEY ("inscriptionId") REFERENCES "Inscription"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Absence_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Absence_seance_inscription_key" ON "Absence"("seanceId", "inscriptionId");
CREATE INDEX "Absence_inscriptionId_idx" ON "Absence"("inscriptionId");

-- Historique du module des absences : enregistrement des feuilles, corrections d'absences, séances
-- modifiées, puis (lot suivant) justifications. Une seule table, l'ancienne et la nouvelle valeur en JSON.
CREATE TABLE "HistoriqueAbsence" (
  "id" TEXT NOT NULL,
  "ficheId" TEXT,
  "classeId" TEXT,
  "date" DATE,
  "action" TEXT NOT NULL,
  "libelle" TEXT NOT NULL,
  "avant" JSONB,
  "apres" JSONB,
  "parId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "HistoriqueAbsence_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "HistoriqueAbsence_ficheId_fkey" FOREIGN KEY ("ficheId") REFERENCES "FicheAppel"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "HistoriqueAbsence_classeId_fkey" FOREIGN KEY ("classeId") REFERENCES "Classe"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "HistoriqueAbsence_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX "HistoriqueAbsence_ficheId_idx" ON "HistoriqueAbsence"("ficheId");
CREATE INDEX "HistoriqueAbsence_creeLe_idx" ON "HistoriqueAbsence"("creeLe");
