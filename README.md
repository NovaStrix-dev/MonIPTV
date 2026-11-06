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
   et téléchargez **MonIPTV-apk** en bas de page (un .zip contenant `monIPTVperso.apk`).

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
- **Sous-titres** : pendant une vidéo, touchez l'écran puis « Style des sous-titres » (en haut à droite)
  pour choisir la taille, la couleur, le contour ou le fond, la position et le gras. Le réglage est gardé
  pour toutes les vidéos.
- **Avec un VPN** : quand un VPN est actif, l'appli passe par la pile réseau standard d'Android (la même que
  les téléchargements). Si ça échoue encore, le message d'erreur propose **« Contourner le VPN pour MonIPTV »** :
  l'appli utilise alors directement le Wi-Fi ou les données mobiles (les autres applis restent dans le VPN).
  Une ligne discrète le rappelle ; la toucher arrête le contournement. Si l'appli VPN interdit ce contournement,
  excluez MonIPTV dans ses réglages (« tunnel fractionné » / *split tunneling*). Les messages d'erreur affichent
  un « Détail » technique : envoyez-en une capture en cas de souci.
- **Favoris** : touchez le petit cœur en haut à droite d'une affiche (sans ouvrir la fiche), le cœur de la
  fiche d'un film ou en haut de l'écran d'une série, ou faites un appui long sur une affiche. Les favoris sont regroupés dans la catégorie **Favoris** des onglets Films et
  Séries (et en tête de « Toutes »). Ils sont conservés même après « Changer de compte ».
- **Synopsis** : la fiche d'un film et l'écran d'une série affichent le résumé, l'année, le genre, la durée
  et la note (comptes Xtream Codes ; les playlists M3U ne fournissent pas de résumé).
- **Tri** : films et séries sont rangés du dernier ajouté au premier, dans chaque catégorie et dans la recherche.
- **Films et Séries** : même barre de recherche compacte et même sélecteur « Catégorie » (liste déroulante) dans les deux onglets.
- **Télécharger une saison complète** : dans l'écran d'une série, bouton « Télécharger la saison N ». Les épisodes sont mis en file d'attente et téléchargés l'un après l'autre (même appli fermée) ; suivi et annulation dans « Hors ligne ».
- **Replier les catégories** : dans la vue « Toutes », touchez le titre d'une catégorie pour la replier ou la déplier ; « Tout replier » / « Tout déplier » à droite du nombre de titres. Le choix est mémorisé.
