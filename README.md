# DanaRanks 🏆

**DanaRanks** est un plugin Minecraft complet et performant développé pour le serveur **DanaKube**. Il propose un système innovant de progression de grade basé sur l'**ELO** des joueurs, rythmé par des **Quotas Périodiques** individuels et des événements compétitifs de **Rush Quotidien**.

Le plugin s'intègre étroitement avec LuckPerms pour gérer les promotions de grades et s'interface avec plusieurs plugins tiers pour traquer les actions des joueurs.

---

## 🛠️ Stack Technique & Prérequis

- **Version Minecraft** : Paper 1.21+
- **Version Java** : JDK 21
- **Gestionnaire de Build** : Maven
- **Dépendances Obligatoires** :
  - **LuckPerms** : Gestion des groupes et pistes de promotion (tracks).
  - **ExcellentEconomy** (et **Vault** optionnel) : Gestion de l'économie (Lumens).
  - **ExcellentJobs** : Suivi des gains d'expérience de métier.
  - **DanaTools** : Suivi de l'expérience d'outils.

---

## ✨ Fonctionnalités Clés

### 📊 1. Système d'ELO & Rangs
- **Progression Linéaire** : Il y a 50 rangs configurés (ex: Fer I, Bronze II...). Chaque rang nécessite **100 ELO** pour passer au suivant.
- **Formule de Promotion** : Si un joueur dépasse 100 ELO, il monte de grade et le reliquat d'ELO est conservé pour le grade suivant. S'il retombe à 0 ELO, il ne peut pas régresser de grade (plancher de sécurité).
- **Intégration LuckPerms** : Lors d'une montée de rang, le plugin utilise l'API de LuckPerms pour promouvoir automatiquement le joueur sur la piste (track) configurée (par défaut `danaranks`).
- **Gestion Offline** : Le chargement, la sauvegarde et l'attribution d'ELO/grades fonctionnent de manière asynchrone, y compris pour les joueurs déconnectés.

### 📅 2. Quotas Périodiques
Les joueurs ont des objectifs d'activité à remplir sur une période donnée (calculée selon une date de référence et une heure de reset globale).
- **Ressources supportées** :
  - `lumens-gained` : Lumens gagnés.
  - `lumens-spent` : Lumens dépensés.
  - `job-xp` : Expérience de métier gagnée (via ExcellentJobs).
  - `tool-xp` : Expérience d'outil gagnée (via DanaTools).
  - `vanilla-xp-gained` : Expérience Minecraft vanilla gagnée.
  - `vanilla-xp-spent` : Expérience Minecraft vanilla dépensée.
- **Scaling des Objectifs** : Les quantités cibles augmentent à chaque rang (par exemple +15% de quantité par rang).
- **Conséquences ELO** :
  - **Succès & Surplus** : Compléter un quota rapporte une quantité de base d'ELO. Continuer au-delà de la cible permet de générer du *surplus ELO* (jusqu'à un maximum configurable).
  - **Échec & Pénalité** : Si un joueur ne remplit pas ses objectifs avant le reset, il subit une pénalité de perte d'ELO.

### ⚡ 3. Rush Quotidien
Le Rush est un événement compétitif temporaire se déclenchant de manière aléatoire chaque jour.
- **Planification Aléatoire** : Chaque jour à une heure configurée (ex: 8h00), le plugin sélectionne aléatoirement une ressource à traquer, une heure de début (dans une plage définie) et une durée (entre 20 et 60 minutes).
- **Annonce & Inscriptions** : Un message de pré-annonce est envoyé 30 minutes avant le début. Les joueurs s'inscrivent via `/rush join`.
- **Interface Visuelle (Boss Bar)** : Une Boss Bar colorée (via Adventure MiniMessage) s'affiche pour les participants pour indiquer le temps restant et leur score en temps réel.
- **Calculateur ELO Dynamique (Zero-Sum)** :
  - Si au moins 2 participants atteignent un score > 0, les gains et pertes d'ELO sont distribués selon un algorithme ELO à somme nulle adapté au rang des joueurs.
  - Si un seul joueur participe, un calcul transversal avec multiplicateurs est appliqué.
- **Intégration Discord** : Des annonces détaillées sont envoyées à un salon Discord via un webhook asynchrone lors de la planification, du lancement et de la fin de l'événement.

---

## 🖥️ Interfaces Graphiques (GUIs)

Les interfaces de DanaRanks utilisent un design premium et réactif :

1. **Profil Joueur (`/profile` ou `/ranks`)** :
   - Affiche la tête du joueur avec son grade actuel, son ELO, et sa position dans le classement.
   - **Frise de Progression (Timeline)** : Représentation visuelle de sa progression avec des icônes de couleurs distinctes : gris pour les rangs passés, bloc d'or pour le rang actuel, rouge pour les rangs futurs.
   - Boutons de redirection vers l'Historique d'ELO et le Leaderboard.
2. **Mes Quotas (`/quota`)** :
   - Affiche la liste des objectifs en cours pour la période actuelle.
   - Représentation visuelle sous forme de barres de progression textuelles (ex: `██████░░░░ 60%`).
   - Affiche une horloge indiquant le temps restant précis avant le prochain reset global.
3. **Classement Global (`/leaderboard` ou `/top`)** :
   - Affiche la liste des 50 meilleurs joueurs du serveur triés par Rang et ELO.
4. **Historique d'ELO** :
   - Liste paginée affichant toutes les transactions d'ELO du joueur (gains de quota, pertes, victoires en rush, ajustements administratifs).

---

## 📜 Commandes & Permissions

### Commandes Joueurs

| Commande | Description | Alias | Permission |
| :--- | :--- | :--- | :--- |
| `/profile` | Ouvre l'interface de profil du joueur. | `/ranks` | Aucune (par défaut) |
| `/quota` | Ouvre l'interface des quotas en cours. | | Aucune (par défaut) |
| `/leaderboard` | Ouvre le classement top 50 du serveur. | `/top`, `/ranksmap` | Aucune (par défaut) |
| `/rush join` | S'inscrire à l'événement de Rush actif. | `/ranksrush` | `danaranks.command.rush` |
| `/rush status` | Consulter l'état et le score du Rush du jour. | `/ranksrush` | `danaranks.command.rush` |
| `/rush leave` | Quitter le Rush en cours (réinitialise le score).| `/ranksrush` | `danaranks.command.rush` |

### Commandes Administrateurs

Toutes les commandes d'administration commencent par `/danaranks` (alias `/dr`) et requièrent la permission **`danaranks.admin`**.

- `/danaranks setrank <joueur> <1-50>` : Modifie le rang d'un joueur (offline compatible).
- `/danaranks setelo <joueur> <elo>` : Modifie l'ELO d'un joueur dans son rang actuel.
- `/danaranks addelo <joueur> <quantité>` : Ajoute de l'ELO (gère les promotions de rangs si > 100).
- `/danaranks removeelo <joueur> <quantité>` : Retire de l'ELO (bloqué au plancher de 0 ELO).
- `/danaranks resetquota <joueur>` : Réinitialise la progression de quota du joueur pour la période en cours.
- `/danaranks rush start <ressource> <durée>` : Force le lancement immédiat d'un Rush.
- `/danaranks rush stop` : Force l'arrêt du Rush en cours.
- `/danaranks reload` : Recharge les fichiers de configuration.

---

## ⚙️ Fichiers de Configuration

Le plugin génère trois fichiers de configuration dans son dossier `plugins/DanaRanks/` :

### `config.yml`
Gère la base de données, la liaison LuckPerms, le reset des quotas et la planification du Rush.

```yaml
database:
  type: SQLITE # SQLITE ou MYSQL
  mysql:
    host: localhost
    port: 3306
    database: danaranks
    username: root
    password: ""
    prefix: "danaranks_"
    pool:
      maximum-pool-size: 10
      max-lifetime: 1800000

luckperms:
  track-name: "danaranks" # Nom de la piste LP

reset:
  hour: 4 # Heure du reset (0-23)
  reference-date: "2026-07-03" # Date de référence pour le calcul des périodes

quotas-settings:
  surplus-multiplier: 10.0 # Surplus maximum ELO autorisé (10x la cible)
  scaling:
    multiplier-per-rank: 1.15 # +15% de cible nécessaire à chaque rang supérieur
  base-rank-1:
    objectives:
      lumens-gained:
        target: 1000
        base-elo: 5
        max-surplus-elo: 10
        fail-penalty: 0

rush:
  daily-setup-hour: 8
  start-window:
    min-hour: 12
    max-hour: 22
  duration-range:
    min-minutes: 20
    max-minutes: 60
  pre-announce-minutes: 30
  discord-webhook-url: ""
  eligible-resources:
    - "lumens-gained"
    - "lumens-spent"
    # ...
```

### `gui.yml`
Permet de personnaliser le titre, la taille et l'emplacement des items dans les différents menus graphiques (profil, quotas, classement, historique).

### `lang/fr.yml`
Contient l'intégralité des messages envoyés aux joueurs. Les messages supportent le format Kyori MiniMessage (ex: `<red>`, `<green>`, `<gradient:gold:yellow>`).
