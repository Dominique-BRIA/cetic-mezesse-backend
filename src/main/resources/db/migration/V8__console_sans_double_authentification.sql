-- La console technique se connecte par e-mail et mot de passe seuls.

ALTER TABLE "AdminConsole"
  DROP COLUMN "totpSecret",
  DROP COLUMN "totpActif",
  DROP COLUMN "totpDernierPas";
