-- Spécialités (RG-29) et journal des décisions du SG (RG-30).

-- CreateEnum
CREATE TYPE "DomaineDecision" AS ENUM ('ORGANISATION', 'SCOLARITE', 'ADMINISTRATION');

-- CreateEnum
CREATE TYPE "StatutDecision" AS ENUM ('APPROUVEE', 'DESAPPROUVEE');

-- CreateTable
CREATE TABLE "Specialite" (
    "id" TEXT NOT NULL,
    "nom" TEXT NOT NULL,
    "nomEn" TEXT,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Specialite_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Specialite_nom_key" ON "Specialite"("nom");

-- AlterTable
ALTER TABLE "Classe" ADD COLUMN "numero" INTEGER NOT NULL DEFAULT 1,
ADD COLUMN "specialiteId" TEXT;

-- Reprise des spécialités déjà saisies en texte libre : une ligne par valeur distincte.
INSERT INTO "Specialite" ("id", "nom")
SELECT 'sp' || substr(md5(s), 1, 23), s
FROM (SELECT DISTINCT trim("specialite") AS s FROM "Classe" WHERE trim(coalesce("specialite", '')) <> '') v;

UPDATE "Classe" c SET "specialiteId" = sp."id"
FROM "Specialite" sp
WHERE sp."nom" = trim(c."specialite");

-- Classes d'un même niveau et d'une même spécialité : numérotées dans l'ordre de leur nom.
-- Les noms existants sont conservés ; ils seront recalculés à la prochaine modification.
UPDATE "Classe" c SET "numero" = r.n
FROM (
  SELECT "id", row_number() OVER (PARTITION BY "anneeId", "niveau", "specialiteId" ORDER BY "nom") AS n
  FROM "Classe"
) r
WHERE r."id" = c."id";

ALTER TABLE "Classe" DROP COLUMN "specialite";

-- CreateTable
CREATE TABLE "Decision" (
    "id" TEXT NOT NULL,
    "domaine" "DomaineDecision" NOT NULL,
    "action" TEXT NOT NULL,
    "libelle" TEXT NOT NULL,
    "lien" TEXT,
    "auteurId" TEXT NOT NULL,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "statut" "StatutDecision" NOT NULL DEFAULT 'APPROUVEE',
    "desapprouveeLe" TIMESTAMP(3),
    "desapprouveeParId" TEXT,
    "motifDesapprobation" TEXT,

    CONSTRAINT "Decision_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Decision_creeLe_idx" ON "Decision"("creeLe");

-- CreateIndex
CREATE INDEX "Decision_domaine_creeLe_idx" ON "Decision"("domaine", "creeLe");

-- CreateIndex
CREATE INDEX "Classe_anneeId_niveau_specialiteId_idx" ON "Classe"("anneeId", "niveau", "specialiteId");

-- AddForeignKey
ALTER TABLE "Classe" ADD CONSTRAINT "Classe_specialiteId_fkey" FOREIGN KEY ("specialiteId") REFERENCES "Specialite"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Decision" ADD CONSTRAINT "Decision_auteurId_fkey" FOREIGN KEY ("auteurId") REFERENCES "Utilisateur"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Decision" ADD CONSTRAINT "Decision_desapprouveeParId_fkey" FOREIGN KEY ("desapprouveeParId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE;
