-- Console technique du développeur : ses comptes (hors des comptes de l'école), son journal, le parc
-- d'appareils mobiles, les plantages remontés et la politique de mise à jour.

CREATE TABLE "AdminConsole" (
  "id" TEXT NOT NULL,
  "email" TEXT NOT NULL,
  "nom" TEXT NOT NULL,
  "motDePasse" TEXT NOT NULL,
  "totpSecret" TEXT,
  "totpActif" BOOLEAN NOT NULL DEFAULT false,
  "totpDernierPas" BIGINT NOT NULL DEFAULT 0,
  "actif" BOOLEAN NOT NULL DEFAULT true,
  "echecs" INTEGER NOT NULL DEFAULT 0,
  "verrouilleJusqua" TIMESTAMP(3),
  "versionSession" INTEGER NOT NULL DEFAULT 0,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AdminConsole_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "AdminConsole_email_key" ON "AdminConsole"("email");

CREATE TABLE "JournalConsole" (
  "id" TEXT NOT NULL,
  "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "adminEmail" TEXT NOT NULL,
  "action" TEXT NOT NULL,
  "cible" TEXT,
  "detail" TEXT,
  "adresse" TEXT,
  CONSTRAINT "JournalConsole_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "JournalConsole_date_idx" ON "JournalConsole"("date");

-- Un téléphone où l'application est installée ; installationId est tiré au sort à la première ouverture.
CREATE TABLE "Appareil" (
  "id" TEXT NOT NULL,
  "installationId" TEXT NOT NULL,
  "utilisateurId" TEXT,
  "numeroVersion" INTEGER NOT NULL,
  "nomVersion" TEXT NOT NULL,
  "correctif" INTEGER,
  "modele" TEXT,
  "versionAndroid" TEXT,
  "premiereVue" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "derniereVue" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "Appareil_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "Appareil_utilisateurId_fkey" FOREIGN KEY ("utilisateurId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "Appareil_installationId_key" ON "Appareil"("installationId");
CREATE INDEX "Appareil_utilisateurId_idx" ON "Appareil"("utilisateurId");

-- Un même plantage (empreinte) sur un même téléphone et une même version tient sur une ligne.
CREATE TABLE "Plantage" (
  "id" TEXT NOT NULL,
  "empreinte" TEXT NOT NULL,
  "installationId" TEXT NOT NULL,
  "type" TEXT NOT NULL,
  "message" TEXT NOT NULL,
  "pile" TEXT,
  "numeroVersion" INTEGER NOT NULL,
  "nomVersion" TEXT NOT NULL,
  "modele" TEXT,
  "versionAndroid" TEXT,
  "occurrences" INTEGER NOT NULL DEFAULT 1,
  "premiereFois" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "derniereFois" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "resolu" BOOLEAN NOT NULL DEFAULT false,
  CONSTRAINT "Plantage_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "Plantage_empreinte_installationId_numeroVersion_key" ON "Plantage"("empreinte", "installationId", "numeroVersion");
CREATE INDEX "Plantage_derniereFois_idx" ON "Plantage"("derniereFois");

-- Une seule ligne : consultée par l'application avant de télécharger une nouvelle version.
CREATE TABLE "PolitiqueMiseAJour" (
  "id" INTEGER NOT NULL DEFAULT 1,
  "suspendue" BOOLEAN NOT NULL DEFAULT false,
  "versionsBloquees" TEXT NOT NULL DEFAULT '',
  "minimum" INTEGER NOT NULL DEFAULT 0,
  "message" TEXT,
  "majLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "majPar" TEXT,
  CONSTRAINT "PolitiqueMiseAJour_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "PolitiqueMiseAJour_une_ligne" CHECK ("id" = 1)
);
INSERT INTO "PolitiqueMiseAJour" ("id") VALUES (1);
