-- Paliers de sanction sur les heures d'absence NON justifiées cumulées depuis la rentrée, en minutes ;
-- vide : palier non utilisé. Seule la sanction la plus élevée atteinte figure au bulletin ; au palier
-- d'exclusion, le Proviseur décide (renvoi) ; sans renvoi, l'élève reçoit le blâme.
ALTER TABLE "Etablissement" ADD COLUMN "seuilAvertissement" INTEGER;
ALTER TABLE "Etablissement" ADD COLUMN "seuilBlame" INTEGER;
ALTER TABLE "Etablissement" ADD COLUMN "seuilExclusion" INTEGER;

-- Élèves dont le Proviseur a été prévenu qu'ils ont atteint le palier d'exclusion (une fois par année :
-- l'inscription est propre à l'année).
CREATE TABLE "AlerteExclusion" (
  "inscriptionId" TEXT NOT NULL,
  "minutes" INTEGER NOT NULL,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AlerteExclusion_pkey" PRIMARY KEY ("inscriptionId"),
  CONSTRAINT "AlerteExclusion_inscriptionId_fkey" FOREIGN KEY ("inscriptionId") REFERENCES "Inscription"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
