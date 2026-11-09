-- Historique jour par jour pour les courbes de la console technique. Appareil et Plantage ne gardent
-- que le dernier état : sans ces tables, impossible de savoir qui était actif il y a une semaine.

-- Un téléphone actif un jour donné tient sur une ligne, quel que soit le nombre d'ouvertures.
CREATE TABLE "ActiviteJour" (
  "jour" DATE NOT NULL,
  "installationId" TEXT NOT NULL,
  "numeroVersion" INTEGER NOT NULL,
  CONSTRAINT "ActiviteJour_pkey" PRIMARY KEY ("jour", "installationId")
);

CREATE TABLE "PlantageJour" (
  "jour" DATE NOT NULL,
  "type" TEXT NOT NULL,
  "numeroVersion" INTEGER NOT NULL,
  "occurrences" INTEGER NOT NULL DEFAULT 1,
  CONSTRAINT "PlantageJour_pkey" PRIMARY KEY ("jour", "type", "numeroVersion")
);
