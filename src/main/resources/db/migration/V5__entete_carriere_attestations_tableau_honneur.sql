-- En-tête officiel : sigle anglais (GTC) à côté du sigle français (CETIC).
-- Tableau d'honneur : moyenne minimale, réglable par le Proviseur et le SG (12,00 par défaut).
ALTER TABLE "Etablissement"
  ADD COLUMN "sigleEn" TEXT,
  ADD COLUMN "seuilTableauHonneur" DECIMAL(4,2) NOT NULL DEFAULT 12;

-- Carrière des personnels, imprimée sur les attestations de présence effective au poste.
ALTER TABLE "Utilisateur"
  ADD COLUMN "sexe" "Sexe",
  ADD COLUMN "matricule" TEXT,
  ADD COLUMN "grade" TEXT,
  ADD COLUMN "fonction" TEXT,
  ADD COLUMN "datePriseService" DATE,
  ADD COLUMN "dateEntreeFonctionPublique" DATE,
  ADD COLUMN "acteNomination" TEXT;

CREATE UNIQUE INDEX "Utilisateur_matricule_key" ON "Utilisateur"("matricule");

-- Registre des attestations de présence effective au poste délivrées par le Proviseur.
-- Les informations imprimées sont gardées telles qu'à la délivrance : une réimpression est identique.
CREATE TABLE "Attestation" (
  "id" TEXT NOT NULL,
  "reference" TEXT NOT NULL,
  "numero" INTEGER NOT NULL,
  "anneeLibelle" TEXT NOT NULL,
  "utilisateurId" TEXT NOT NULL,
  "beneficiaire" TEXT NOT NULL,
  "sexe" "Sexe",
  "matricule" TEXT NOT NULL,
  "grade" TEXT NOT NULL,
  "fonction" TEXT NOT NULL,
  "datePriseService" DATE NOT NULL,
  "acteNomination" TEXT,
  "signataire" TEXT NOT NULL,
  "parId" TEXT NOT NULL,
  "delivreeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "Attestation_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "Attestation_reference_key" ON "Attestation"("reference");
CREATE INDEX "Attestation_utilisateurId_idx" ON "Attestation"("utilisateurId");

ALTER TABLE "Attestation" ADD CONSTRAINT "Attestation_utilisateurId_fkey"
  FOREIGN KEY ("utilisateurId") REFERENCES "Utilisateur"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "Attestation" ADD CONSTRAINT "Attestation_parId_fkey"
  FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
