-- Règles de calcul des résultats, réglées par le SG dans les paramètres :
-- - moyenne de délibération : moyenne annuelle qui permet le passage en classe supérieure (vide : pas de délibération) ;
-- - heures d'absence non justifiées sur la période à partir desquelles le tableau d'honneur est refusé (vide : pas de limite) ;
-- - part des matières composées (en %) : en dessous de "seuilNonClasse" l'élève n'est pas classé ; en dessous de
--   "seuilMatieresIgnorees" les matières non composées valent 0 ; au-delà, elles sont ignorées ;
-- - ordre de départage de la première place : liste de critères (groupe de matières, discipline, ordre alphabétique).
ALTER TABLE "Etablissement"
  ADD COLUMN "moyenneDeliberation" DECIMAL(4,2),
  ADD COLUMN "heuresRefusTableauHonneur" INTEGER,
  ADD COLUMN "seuilNonClasse" DECIMAL(5,2) NOT NULL DEFAULT 33.33,
  ADD COLUMN "seuilMatieresIgnorees" DECIMAL(5,2) NOT NULL DEFAULT 66.67,
  ADD COLUMN "criteresDepartage" JSONB;
