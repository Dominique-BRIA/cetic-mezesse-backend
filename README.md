# API du CETIC de Mezesse — Spring Boot

Réécriture en Spring Boot de l'API Node, qui reste dans le dépôt [cetic-mezesse-mobile](https://github.com/Dominique-BRIA/cetic-mezesse-mobile) (dossier `api/`). Mêmes routes, mêmes réponses JSON, même base PostgreSQL : le site web et l'application mobile fonctionnent sans changement.

Java 21, Spring Boot 3.5, Maven.

## Organisation

```text
src/main/java/cm/cetic/mezesse/
  config/       démarrage : .env, URL de base Prisma → JDBC, sécurité, JSON
  security/     jetons JWT, rôles (@Direction, @SurveillantGeneral, @Proviseur, @Professeur), limite de connexion
  entity/       entités JPA, une par table (schéma inchangé, noms de colonnes conservés)
  repository/   accès aux données (Spring Data JPA)
  dto/          formes des requêtes et des réponses JSON, avec leur validation
  service/      règles de gestion (RG-01 à RG-32) ; service/calcul : moyennes, rangs, mentions
  controller/   routes /api/...
  document/     PDF (bulletins, listes, cartes scolaires) et Excel
  exception/    erreurs renvoyées en { "message": … }
src/main/resources/
  application.yml
  db/migration/ migrations Flyway (V1 à V4 = les 4 migrations Prisma)
```

## Lancer en local

Un fichier `.env` à la racine du dépôt (jamais commité) reprend les variables de l'API Node, avec les mêmes noms : `DATABASE_URL`, `JWT_SECRET`, `PORT`, `CORS_ORIGINS`, `B2_*`, `SEED_*`. Sans B2, les photos sont lues et écrites dans le dossier `DOSSIER_PHOTOS` (`uploads` par défaut).

Le disque C: étant presque plein, le dépôt Maven local est sur D: :

```bash
mvn -Dmaven.repo.local=D:/Dev/.m2/repository spring-boot:run      # API sur PORT (4000 par défaut)
mvn -Dmaven.repo.local=D:/Dev/.m2/repository test                 # tests du calcul et des règles
mvn -Dmaven.repo.local=D:/Dev/.m2/repository package              # target/cetic-api.jar
java -jar target/cetic-api.jar --amorcer                          # base vide : établissement, barèmes, année, comptes
```

Au démarrage, Flyway applique les migrations. Sur une base créée par Prisma, il prend les 4 migrations existantes comme point de départ (version 4) sans les rejouer. Les nouvelles migrations s'ajoutent dans `db/migration` sous la forme `V5__description.sql`.

## Compatibilité avec l'API Node

Vérifiée en faisant tourner les deux API côte à côte, chacune sur une copie de la même base, et en comparant leurs réponses :

- 53 routes en lecture (les trois rôles, erreurs et documents compris) : réponses identiques ;
- 66 étapes d'écriture : création, modification et suppression, notes, renvois, et désapprobations du Proviseur avec annulation automatique. Les opérations donnent les mêmes résultats ; seuls quelques messages d'erreur de validation sont formulés autrement (en français au lieu des messages anglais de zod) ;
- 16 envois de fichiers (photos, saisie groupée, import Excel ou CSV) : identiques, à un libellé d'erreur près ;
- bulletins, listes et cartes scolaires : même rendu, avec des PDF 5 à 7 fois plus légers.

Les jetons de connexion sont compatibles : une session ouverte avec l'API Node reste valable après la bascule.

Deux formats diffèrent, sans effet sur les clients actuels :

- les dates de naissance et de renvoi s'écrivent `2011-05-26` au lieu de `2011-05-26T00:00:00.000Z` ;
- les nombres décimaux s'écrivent `16.0` au lieu de `16`.

## Déploiement sur Azure

Le déploiement est automatique. À chaque push sur `main`, le workflow « API Azure » (`.github/workflows/deploiement.yml`) :

1. compile et teste l'API ;
2. envoie **le jar** sur l'App Service `cetic-mezesse-api`.

Azure ne compile ni n'installe rien : il remplace `app.jar` et redémarre, et Flyway applique les migrations au démarrage. L'option `clean` vide le dossier du site avant l'envoi, si bien qu'il ne reste que le jar.

### Une seule fois : passer l'App Service de Node à Java

Dans Azure Cloud Shell (Bash), avant le premier déploiement du jar :

```bash
RG=$(az webapp list --query "[?name=='cetic-mezesse-api'].resourceGroup" -o tsv)
az webapp config set -g $RG -n cetic-mezesse-api --linux-fx-version "JAVA|21-java21" --generic-configurations '{"appCommandLine": ""}'
az webapp config appsettings set -g $RG -n cetic-mezesse-api --settings JAVA_OPTS="-Xmx512m"
az webapp config show -g $RG -n cetic-mezesse-api --query "{pile: linuxFxVersion, demarrage: appCommandLine}"
```

La dernière commande doit afficher la pile `JAVA|21-java21` et une commande de démarrage vide ; Azure lance alors `app.jar`.

### Une seule fois : brancher ce dépôt sur Azure

Dans GitHub, Settings → Secrets and variables → Actions :

- secret `AZURE_WEBAPP_PUBLISH_PROFILE` : le profil de publication de l'App Service (portail Azure → App Service → « Télécharger le profil de publication ») ;
- variable `API_URL` : l'adresse de l'API, terminée par `/api` ;
- variable `DEPLOYER_SUR_AZURE` = `oui`. Tant qu'elle n'existe pas, le workflow compile et teste sans rien envoyer.

Les autres paramètres d'application ne changent pas : `DATABASE_URL`, `DIRECT_URL`, `JWT_SECRET`, `CORS_ORIGINS` et `B2_*`.

### Revenir à l'API Node

1. Remettre la pile `NODE|22-lts` avec la commande de démarrage `npm run demarrage`.
2. Mettre `DEPLOYER_SUR_AZURE` à `non` ici.
3. Dans le dépôt cetic-mezesse-mobile, réactiver et lancer à la main le workflow « API Azure » (`api.yml`).

La base n'a pas à être restaurée : Flyway n'y ajoute que sa table `flyway_schema_history`.
