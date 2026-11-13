-- Sanctions pour absences non justifiées, remplies par le SG comme les cotes : autant de sanctions que
-- l'établissement en applique, chacune à partir d'un nombre d'heures (en minutes, inclus) cumulées depuis la
-- rentrée, jusqu'à la suivante. « ligne » : ligne du bloc « Discipline » du bulletin qui la porte
-- (AVERTISSEMENT, BLAME, EXCLUSION ou AUCUNE). Une sanction d'exclusion est décidée par le Proviseur.
CREATE TABLE "SanctionAbsence" (
  "id" TEXT NOT NULL,
  "libelle" TEXT NOT NULL,
  "ligne" TEXT NOT NULL,
  "minutes" INTEGER NOT NULL,
  CONSTRAINT "SanctionAbsence_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "SanctionAbsence_ligne_check" CHECK ("ligne" IN ('AVERTISSEMENT', 'BLAME', 'EXCLUSION', 'AUCUNE')),
  CONSTRAINT "SanctionAbsence_minutes_check" CHECK ("minutes" > 0)
);
CREATE UNIQUE INDEX "SanctionAbsence_minutes_key" ON "SanctionAbsence"("minutes");

-- Les trois paliers fixes de V15 deviennent des sanctions de la liste.
INSERT INTO "SanctionAbsence" ("id", "libelle", "ligne", "minutes")
SELECT 'sanction-avertissement', 'Avertissement', 'AVERTISSEMENT', "seuilAvertissement" FROM "Etablissement" WHERE "seuilAvertissement" > 0
UNION ALL
SELECT 'sanction-blame', 'Blâme', 'BLAME', "seuilBlame" FROM "Etablissement" WHERE "seuilBlame" > 0
UNION ALL
SELECT 'sanction-exclusion', 'Exclusion', 'EXCLUSION', "seuilExclusion" FROM "Etablissement" WHERE "seuilExclusion" > 0
ON CONFLICT DO NOTHING;

ALTER TABLE "Etablissement" DROP COLUMN "seuilAvertissement", DROP COLUMN "seuilBlame", DROP COLUMN "seuilExclusion";
