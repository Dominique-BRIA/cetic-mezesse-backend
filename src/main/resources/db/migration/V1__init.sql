-- CreateEnum
CREATE TYPE "Role" AS ENUM ('PROVISEUR', 'SURVEILLANT_GENERAL', 'PROFESSEUR');

-- CreateEnum
CREATE TYPE "Sexe" AS ENUM ('M', 'F');

-- CreateEnum
CREATE TYPE "StatutInscription" AS ENUM ('ACTIF', 'RENVOYE');

-- CreateEnum
CREATE TYPE "TypeBareme" AS ENUM ('GRADE', 'COTE');

-- CreateTable
CREATE TABLE "Utilisateur" (
    "id" TEXT NOT NULL,
    "nom" TEXT NOT NULL,
    "prenom" TEXT NOT NULL,
    "telephone" TEXT,
    "email" TEXT,
    "motDePasse" TEXT NOT NULL,
    "role" "Role" NOT NULL,
    "actif" BOOLEAN NOT NULL DEFAULT true,
    "doitChangerMotDePasse" BOOLEAN NOT NULL DEFAULT false,
    "discipline" TEXT,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "majLe" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Utilisateur_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Etablissement" (
    "id" INTEGER NOT NULL DEFAULT 1,
    "nomFr" TEXT NOT NULL,
    "nomEn" TEXT NOT NULL,
    "sigle" TEXT NOT NULL,
    "regionFr" TEXT NOT NULL,
    "regionEn" TEXT NOT NULL,
    "departementFr" TEXT NOT NULL,
    "departementEn" TEXT NOT NULL,
    "arrondissement" TEXT NOT NULL,
    "localite" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "boitePostale" TEXT,
    "telephone" TEXT,
    "prefixeMatricule" TEXT NOT NULL DEFAULT 'CTM',

    CONSTRAINT "Etablissement_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AnneeScolaire" (
    "id" TEXT NOT NULL,
    "libelle" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT false,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AnneeScolaire_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Sequence" (
    "id" TEXT NOT NULL,
    "anneeId" TEXT NOT NULL,
    "numero" INTEGER NOT NULL,
    "trimestre" INTEGER NOT NULL,
    "saisieOuverte" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "Sequence_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Classe" (
    "id" TEXT NOT NULL,
    "anneeId" TEXT NOT NULL,
    "nom" TEXT NOT NULL,
    "nomEn" TEXT,
    "niveau" INTEGER NOT NULL,
    "specialite" TEXT,
    "profPrincipalId" TEXT,

    CONSTRAINT "Classe_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GroupeMatiere" (
    "id" TEXT NOT NULL,
    "anneeId" TEXT NOT NULL,
    "nom" TEXT NOT NULL,
    "nomEn" TEXT,
    "ordre" INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT "GroupeMatiere_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Matiere" (
    "id" TEXT NOT NULL,
    "classeId" TEXT NOT NULL,
    "groupeId" TEXT NOT NULL,
    "nom" TEXT NOT NULL,
    "nomEn" TEXT,
    "coefficient" INTEGER NOT NULL,
    "ordre" INTEGER NOT NULL DEFAULT 1,
    "professeurId" TEXT,

    CONSTRAINT "Matiere_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Eleve" (
    "id" TEXT NOT NULL,
    "matricule" TEXT NOT NULL,
    "matriculeNational" TEXT,
    "nom" TEXT NOT NULL,
    "prenoms" TEXT NOT NULL,
    "sexe" "Sexe" NOT NULL,
    "dateNaissance" DATE NOT NULL,
    "lieuNaissance" TEXT NOT NULL,
    "photoCle" TEXT,
    "parentNom" TEXT,
    "parentTelephone" TEXT,
    "parentAdresse" TEXT,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "majLe" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Eleve_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Inscription" (
    "id" TEXT NOT NULL,
    "eleveId" TEXT NOT NULL,
    "anneeId" TEXT NOT NULL,
    "classeId" TEXT NOT NULL,
    "redoublant" BOOLEAN NOT NULL DEFAULT false,
    "statut" "StatutInscription" NOT NULL DEFAULT 'ACTIF',
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Inscription_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Renvoi" (
    "id" TEXT NOT NULL,
    "inscriptionId" TEXT NOT NULL,
    "date" DATE NOT NULL,
    "motif" TEXT NOT NULL,
    "parId" TEXT NOT NULL,
    "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "reintegreLe" TIMESTAMP(3),
    "reintegreParId" TEXT,
    "motifReintegration" TEXT,

    CONSTRAINT "Renvoi_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Note" (
    "id" TEXT NOT NULL,
    "inscriptionId" TEXT NOT NULL,
    "matiereId" TEXT NOT NULL,
    "sequenceId" TEXT NOT NULL,
    "valeur" DECIMAL(4,2) NOT NULL,
    "saisieParId" TEXT NOT NULL,
    "majLe" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Note_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CompetenceVisee" (
    "matiereId" TEXT NOT NULL,
    "sequenceId" TEXT NOT NULL,
    "texte" TEXT NOT NULL,

    CONSTRAINT "CompetenceVisee_pkey" PRIMARY KEY ("matiereId","sequenceId")
);

-- CreateTable
CREATE TABLE "Bareme" (
    "id" TEXT NOT NULL,
    "type" "TypeBareme" NOT NULL,
    "libelle" TEXT NOT NULL,
    "libelleLong" TEXT,
    "min" DECIMAL(4,2) NOT NULL,

    CONSTRAINT "Bareme_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Compteur" (
    "cle" TEXT NOT NULL,
    "valeur" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "Compteur_pkey" PRIMARY KEY ("cle")
);

-- CreateTable
CREATE TABLE "_ClasseToGroupeMatiere" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_ClasseToGroupeMatiere_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "Utilisateur_telephone_key" ON "Utilisateur"("telephone");

-- CreateIndex
CREATE UNIQUE INDEX "Utilisateur_email_key" ON "Utilisateur"("email");

-- CreateIndex
CREATE UNIQUE INDEX "AnneeScolaire_libelle_key" ON "AnneeScolaire"("libelle");

-- CreateIndex
CREATE UNIQUE INDEX "Sequence_anneeId_numero_key" ON "Sequence"("anneeId", "numero");

-- CreateIndex
CREATE UNIQUE INDEX "Classe_anneeId_nom_key" ON "Classe"("anneeId", "nom");

-- CreateIndex
CREATE UNIQUE INDEX "GroupeMatiere_anneeId_nom_key" ON "GroupeMatiere"("anneeId", "nom");

-- CreateIndex
CREATE UNIQUE INDEX "Matiere_classeId_nom_key" ON "Matiere"("classeId", "nom");

-- CreateIndex
CREATE UNIQUE INDEX "Eleve_matricule_key" ON "Eleve"("matricule");

-- CreateIndex
CREATE UNIQUE INDEX "Eleve_matriculeNational_key" ON "Eleve"("matriculeNational");

-- CreateIndex
CREATE INDEX "Inscription_classeId_statut_idx" ON "Inscription"("classeId", "statut");

-- CreateIndex
CREATE UNIQUE INDEX "Inscription_eleveId_anneeId_key" ON "Inscription"("eleveId", "anneeId");

-- CreateIndex
CREATE INDEX "Note_matiereId_sequenceId_idx" ON "Note"("matiereId", "sequenceId");

-- CreateIndex
CREATE UNIQUE INDEX "Note_inscriptionId_matiereId_sequenceId_key" ON "Note"("inscriptionId", "matiereId", "sequenceId");

-- CreateIndex
CREATE UNIQUE INDEX "Bareme_type_libelle_key" ON "Bareme"("type", "libelle");

-- CreateIndex
CREATE INDEX "_ClasseToGroupeMatiere_B_index" ON "_ClasseToGroupeMatiere"("B");

-- AddForeignKey
ALTER TABLE "Sequence" ADD CONSTRAINT "Sequence_anneeId_fkey" FOREIGN KEY ("anneeId") REFERENCES "AnneeScolaire"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Classe" ADD CONSTRAINT "Classe_anneeId_fkey" FOREIGN KEY ("anneeId") REFERENCES "AnneeScolaire"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Classe" ADD CONSTRAINT "Classe_profPrincipalId_fkey" FOREIGN KEY ("profPrincipalId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "GroupeMatiere" ADD CONSTRAINT "GroupeMatiere_anneeId_fkey" FOREIGN KEY ("anneeId") REFERENCES "AnneeScolaire"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Matiere" ADD CONSTRAINT "Matiere_classeId_fkey" FOREIGN KEY ("classeId") REFERENCES "Classe"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Matiere" ADD CONSTRAINT "Matiere_groupeId_fkey" FOREIGN KEY ("groupeId") REFERENCES "GroupeMatiere"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Matiere" ADD CONSTRAINT "Matiere_professeurId_fkey" FOREIGN KEY ("professeurId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Inscription" ADD CONSTRAINT "Inscription_eleveId_fkey" FOREIGN KEY ("eleveId") REFERENCES "Eleve"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Inscription" ADD CONSTRAINT "Inscription_anneeId_fkey" FOREIGN KEY ("anneeId") REFERENCES "AnneeScolaire"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Inscription" ADD CONSTRAINT "Inscription_classeId_fkey" FOREIGN KEY ("classeId") REFERENCES "Classe"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Renvoi" ADD CONSTRAINT "Renvoi_inscriptionId_fkey" FOREIGN KEY ("inscriptionId") REFERENCES "Inscription"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Renvoi" ADD CONSTRAINT "Renvoi_parId_fkey" FOREIGN KEY ("parId") REFERENCES "Utilisateur"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Renvoi" ADD CONSTRAINT "Renvoi_reintegreParId_fkey" FOREIGN KEY ("reintegreParId") REFERENCES "Utilisateur"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Note" ADD CONSTRAINT "Note_inscriptionId_fkey" FOREIGN KEY ("inscriptionId") REFERENCES "Inscription"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Note" ADD CONSTRAINT "Note_matiereId_fkey" FOREIGN KEY ("matiereId") REFERENCES "Matiere"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Note" ADD CONSTRAINT "Note_sequenceId_fkey" FOREIGN KEY ("sequenceId") REFERENCES "Sequence"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Note" ADD CONSTRAINT "Note_saisieParId_fkey" FOREIGN KEY ("saisieParId") REFERENCES "Utilisateur"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CompetenceVisee" ADD CONSTRAINT "CompetenceVisee_matiereId_fkey" FOREIGN KEY ("matiereId") REFERENCES "Matiere"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CompetenceVisee" ADD CONSTRAINT "CompetenceVisee_sequenceId_fkey" FOREIGN KEY ("sequenceId") REFERENCES "Sequence"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_ClasseToGroupeMatiere" ADD CONSTRAINT "_ClasseToGroupeMatiere_A_fkey" FOREIGN KEY ("A") REFERENCES "Classe"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_ClasseToGroupeMatiere" ADD CONSTRAINT "_ClasseToGroupeMatiere_B_fkey" FOREIGN KEY ("B") REFERENCES "GroupeMatiere"("id") ON DELETE CASCADE ON UPDATE CASCADE;
