-- Lignes horaires de l'emploi du temps (07:30 – 08:25…), saisies avant les matières : on remplit d'abord les
-- heures de la journée, puis chaque case avec une matière. Une ligne sans cours reste affichée, vide.
CREATE TABLE "PlageHoraire" (
  "id" TEXT NOT NULL,
  "emploiId" TEXT NOT NULL,
  "debut" TIME(0) NOT NULL,
  "fin" TIME(0) NOT NULL,
  CONSTRAINT "PlageHoraire_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "PlageHoraire_emploiId_fkey" FOREIGN KEY ("emploiId") REFERENCES "EmploiDuTemps"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "PlageHoraire_horaire_check" CHECK ("fin" > "debut")
);
CREATE UNIQUE INDEX "PlageHoraire_emploi_horaire_key" ON "PlageHoraire"("emploiId", "debut", "fin");
