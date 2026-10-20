-- Sessions prolongées (RG-04) : chaque compte porte un numéro de version de session, inscrit dans
-- ses jetons de connexion. Changer ou réinitialiser le mot de passe l'augmente, ce qui ferme les
-- sessions déjà ouvertes sur les autres appareils.
ALTER TABLE "Utilisateur" ADD COLUMN "versionSession" INTEGER NOT NULL DEFAULT 0;
