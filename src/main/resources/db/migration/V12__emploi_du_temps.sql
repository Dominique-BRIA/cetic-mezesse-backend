-- Emploi du temps d'une classe : une semaine type, valable à partir d'une date. Une nouvelle version
-- (nouvelle date d'effet) remplace la précédente pour les jours suivants ; les jours passés gardent
-- l'ancienne. La première version (« valableDu » vide) vaut depuis la rentrée.
CREATE TABLE "EmploiDuTemps" (
  "id" TEXT NOT NULL,
  "classeId" TEXT NOT NULL,
  "valableDu" DATE,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "EmploiDuTemps_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "EmploiDuTemps_classeId_fkey" FOREIGN KEY ("classeId") REFERENCES "Classe"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
-- Une seule version par classe et par date d'effet (et une seule « depuis la rentrée »).
CREATE UNIQUE INDEX "EmploiDuTemps_classe_date_key" ON "EmploiDuTemps"("classeId", COALESCE("valableDu", DATE '0001-01-01'));

-- Un créneau : un jour de la semaine (1 = lundi … 6 = samedi), une heure de début et de fin, une matière
-- (qui donne la classe et l'enseignant). Les durées sont en minutes réelles.
CREATE TABLE "Creneau" (
  "id" TEXT NOT NULL,
  "emploiId" TEXT NOT NULL,
  "matiereId" TEXT NOT NULL,
  "jour" INTEGER NOT NULL,
  "debut" TIME(0) NOT NULL,
  "fin" TIME(0) NOT NULL,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "Creneau_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Creneau_emploiId_fkey" FOREIGN KEY ("emploiId") REFERENCES "EmploiDuTemps"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Creneau_matiereId_fkey" FOREIGN KEY ("matiereId") REFERENCES "Matiere"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "Creneau_jour_check" CHECK ("jour" BETWEEN 1 AND 6),
  CONSTRAINT "Creneau_horaire_check" CHECK ("fin" > "debut")
);
CREATE INDEX "Creneau_emploiId_idx" ON "Creneau"("emploiId");
CREATE INDEX "Creneau_matiereId_idx" ON "Creneau"("matiereId");
