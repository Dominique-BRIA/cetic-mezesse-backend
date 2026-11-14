-- Sanctions pour absences non justifiées : trois paliers fixes, que le SG règle en heures (comme les cotes) :
-- « Avertissement conduite », « Blâme conduite », « Exclusion ». Un palier sans heures n'est pas utilisé.
DELETE FROM "SanctionAbsence" WHERE "ligne" = 'AUCUNE';
DELETE FROM "SanctionAbsence" s WHERE EXISTS (SELECT 1 FROM "SanctionAbsence" t WHERE t."ligne" = s."ligne" AND t."minutes" < s."minutes");
UPDATE "SanctionAbsence" SET "libelle" = CASE "ligne" WHEN 'AVERTISSEMENT' THEN 'Avertissement conduite' WHEN 'BLAME' THEN 'Blâme conduite' ELSE 'Exclusion' END;
ALTER TABLE "SanctionAbsence" DROP CONSTRAINT "SanctionAbsence_ligne_check";
ALTER TABLE "SanctionAbsence" ADD CONSTRAINT "SanctionAbsence_ligne_check" CHECK ("ligne" IN ('AVERTISSEMENT', 'BLAME', 'EXCLUSION'));
CREATE UNIQUE INDEX "SanctionAbsence_ligne_key" ON "SanctionAbsence"("ligne");

-- Décision du Directeur (ou de la Directrice) quand un élève atteint les heures d'exclusion :
--   TEMPORAIRE : renvoyé N jours ouvrés (choisis par le Directeur), puis réintégré (un renvoi avec sa fin prévue) ;
--   DEFINITIVE : radié définitivement (un renvoi sans réintégration ; jamais réinscrit) ;
--   SURSIS     : maintenu en classe, le bulletin porte le blâme avec la mention du sursis.
-- « date » : début de l'exclusion, ou jour du sursis ; « sequence » : numéro de la séquence dont les bulletins
-- portent la décision (comme une feuille d'appel : la dernière séquence ouverte) ; « minutes » : heures non
-- justifiées au moment de la décision.
CREATE TABLE "DecisionExclusion" (
  "id" TEXT NOT NULL,
  "inscriptionId" TEXT NOT NULL,
  "type" TEXT NOT NULL,
  "jours" INTEGER,
  "date" DATE NOT NULL,
  "sequence" INTEGER NOT NULL,
  "motif" TEXT,
  "minutes" INTEGER NOT NULL,
  "renvoiId" TEXT,
  "parId" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "DecisionExclusion_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "DecisionExclusion_type_check" CHECK ("type" IN ('TEMPORAIRE', 'DEFINITIVE', 'SURSIS')),
  CONSTRAINT "DecisionExclusion_jours_check" CHECK (("type" = 'TEMPORAIRE' AND "jours" >= 1) OR ("type" <> 'TEMPORAIRE' AND "jours" IS NULL)),
  CONSTRAINT "DecisionExclusion_inscriptionId_fkey" FOREIGN KEY ("inscriptionId") REFERENCES "Inscription"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "DecisionExclusion_renvoiId_fkey" FOREIGN KEY ("renvoiId") REFERENCES "Renvoi"("id") ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT "DecisionExclusion_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX "DecisionExclusion_inscriptionId_idx" ON "DecisionExclusion"("inscriptionId");
