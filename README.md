# MonIPTV — application IPTV Android personnelle

- Connexion **Xtream Codes** (serveur + identifiant + mot de passe) ou **lien M3U**.
- Identifiants chiffrés sur le téléphone : demandés **une seule fois**.
- TV en direct, Films, Séries (saisons / épisodes), recherche et filtres par catégorie.
- **Téléchargement** des films et épisodes, lecture **hors ligne** (onglet « Hors ligne »).
- Reprise automatique des films/épisodes là où vous vous étiez arrêté.
- Android 8.0 minimum.

## Obtenir l'APK — option 1 : GitHub (sans rien installer)

1. Créez un compte gratuit sur https://github.com puis un **nouveau dépôt privé** (bouton « New »).
2. Sur la page du dépôt : **Add file → Upload files**, glissez **tout le contenu** du dossier `MonIPTV`
   (y compris le dossier `.github`), puis **Commit changes**.
   > Si le dossier `.github` n'apparaît pas (fichier caché), créez à la main le fichier
   > `.github/workflows/build-apk.yml` via **Add file → Create new file** et collez-y son contenu.
3. Onglet **Actions** : la compilation démarre seule (≈ 5 min). Quand elle est verte, ouvrez-la
   et téléchargez **MonIPTV-apk** en bas de page (un .zip contenant `app-release.apk`).

## Obtenir l'APK — option 2 : Android Studio

Ouvrez le dossier `MonIPTV` dans Android Studio, attendez la synchronisation, puis
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
L'APK se trouve dans `app/build/outputs/apk/`.

## Installer sur le téléphone

Copiez l'APK sur le téléphone, ouvrez-le, et autorisez « Installer des applis inconnues »
quand Android le demande. Les futures versions s'installent par-dessus sans perdre
la configuration (la clé de signature `app/moniptv.jks` est fixe — gardez-la).

## Bon à savoir

- Beaucoup d'abonnements n'autorisent **qu'une connexion à la fois** : un téléchargement en
  cours peut empêcher de regarder autre chose en même temps.
- Certains fichiers MKV utilisent un son DTS/AC3 que tous les téléphones ne savent pas décoder
  (image sans son). Le cas échéant, préférez la version MP4 du film si votre fournisseur la propose.
- Les téléchargements sont stockés dans l'espace privé de l'appli : ils sont supprimés si
  vous désinstallez l'application.
- Bouton ⟳ : met à jour le catalogue. Bouton de sortie : change de compte (garde les téléchargements).
