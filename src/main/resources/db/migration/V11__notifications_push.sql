-- Notifications push (Firebase Cloud Messaging).

-- Téléphones : le jeton est envoyé par l'application avec son signal d'ouverture. Les notifications vont
-- aux téléphones du compte connecté (utilisateurId).
ALTER TABLE "Appareil" ADD COLUMN "jetonPush" TEXT;

-- Navigateurs (site web) : un jeton par navigateur où le compte a accepté les notifications. Gardés à
-- part des téléphones pour ne pas fausser le suivi des versions de l'application.
CREATE TABLE "JetonWeb" (
  "id" TEXT NOT NULL,
  "jeton" TEXT NOT NULL,
  "utilisateurId" TEXT NOT NULL,
  "navigateur" TEXT,
  "creeLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "vuLe" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "JetonWeb_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "JetonWeb_utilisateurId_fkey" FOREIGN KEY ("utilisateurId") REFERENCES "Utilisateur"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX "JetonWeb_jeton_key" ON "JetonWeb"("jeton");
CREATE INDEX "JetonWeb_utilisateurId_idx" ON "JetonWeb"("utilisateurId");
