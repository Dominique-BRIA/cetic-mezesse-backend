-- Désapprouver une décision défait l'action (RG-30).
ALTER TABLE "Decision" ADD COLUMN "annulation" JSONB,
ADD COLUMN "annulee" BOOLEAN NOT NULL DEFAULT false;
