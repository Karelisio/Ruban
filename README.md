# Ruban 🎀

Les idées cadeaux de tes proches, notées au fil de l'eau. Ruban suit aussi les occasions, les budgets et l'historique, pour ne plus jamais être pris de court ni offrir deux fois la même chose.

**100 % local** : sans compte, sans pub, sans pistage, sans serveur. L'appli ne se connecte au réseau que pour lire les pages produit que tu partages, suivre leurs prix et chercher les mises à jour sur GitHub.

- Kotlin, Jetpack Compose, Material 3 Expressive (couleurs dynamiques, MaterialShapes)
- Room, DataStore, Navigation Compose, WorkManager, AlarmManager (alarmes exactes)
- Glance (widgets), OkHttp + Jsoup (extraction Open Graph / JSON-LD), BiometricPrompt
- Android 12 minimum (minSdk 31)

## Fonctionnalités

| | |
|---|---|
| Proches | Photo, relation, anniversaire, fête (suggérée d'après le prénom), tailles, goûts, choses à éviter, notes, import des anniversaires depuis les contacts |
| Capture | Menu Partager (liens, textes, images), lecture auto du titre, de l'image et du prix, photo, dictée vocale, raccourcis, widget, tuile |
| Suivi | Idée → achetée → emballée → offerte, réaction et photo du moment, alerte « déjà offert le … » |
| Événements | Anniversaires, fêtes, Noël, Saint-Valentin, fêtes des mères et des pères (dates françaises), événements perso ; rappels J-30 / J-7 / J-1 réglables |
| Budget | Par événement et par proche, récap annuel animé |
| Prix | Vérification quotidienne, historique, alerte de baisse ou sous un seuil |
| Inspire-moi | 605 suggestions hors ligne, filtrées selon les goûts, la relation, l'occasion et le budget, à swiper |
| Discrétion | Verrou biométrique, notifications neutres, aperçu masqué (FLAG_SECURE) |
| Sauvegarde | Fichier `.ruban` chiffré AES-256-GCM (photos incluses), sauvegarde hebdomadaire automatique |

## Étapes manuelles (une seule fois)

### 1. Créer le keystore de signature

Toujours le **même** keystore : sans lui, les mises à jour ne pourront plus s'installer par-dessus l'ancienne version.

```bash
keytool -genkeypair -v \
  -keystore ruban.jks -storetype PKCS12 \
  -alias ruban -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Ruban, O=Karelisio, C=FR"
```

Garde `ruban.jks` et ses mots de passe dans un gestionnaire de mots de passe. **Ne le commite jamais** (le `.gitignore` exclut `*.jks`).

### 2. Ajouter les secrets GitHub

Dans *Settings → Secrets and variables → Actions → New repository secret* :

| Secret | Valeur |
|---|---|
| `RUBAN_KEYSTORE_BASE64` | sortie de `base64 -w0 ruban.jks` (macOS : `base64 -i ruban.jks`) |
| `RUBAN_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `RUBAN_KEY_ALIAS` | `ruban` |
| `RUBAN_KEY_PASSWORD` | mot de passe de la clé (le même qu'au-dessus pour un PKCS12) |

### 3. Publier une version

```bash
git tag v0.1.0
git push origin v0.1.0
```

Le workflow **Release** lance les tests, construit l'APK signé (R8 activé), vérifie la signature et publie une GitHub Release `Ruban-v0.1.0.apk` avec le changelog (messages de commit depuis le tag précédent). `versionCode` = numéro d'exécution du workflow, `versionName` = tag sans le `v`.

Le dépôt est public : l'appli lit directement `releases/latest` de `Karelisio/Ruban`. S'il devient privé, crée un dépôt public dédié (ex. `Karelisio/Ruban-releases`), publies-y les APK et ajoute la variable d'environnement `RUBAN_UPDATE_REPO` au workflow.

### 4. Première installation sur le téléphone

1. Télécharge l'APK depuis la page *Releases* et ouvre-le ; autorise l'installation depuis le navigateur.
2. Au premier lancement, l'onboarding demande toutes les autorisations, une par page (chacune peut attendre).
3. **Honor / MagicOS** : en plus de l'exclusion batterie demandée dans l'appli, va dans *Paramètres → Batterie → Lancement d'applis → Ruban* et choisis *Gérer manuellement* (lancement auto, lancement secondaire, exécution en arrière-plan). Sans ça, MagicOS peut supprimer les rappels.

Les versions suivantes s'installent depuis l'appli : au lancement (au plus toutes les 6 h), Ruban affiche le changelog et propose de télécharger puis d'installer.

## Développer

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- JDK 21, Android SDK avec la plateforme 37.
- Banque de suggestions : modifier `tools/suggestions/*.txt` puis lancer `python3 tools/build_suggestions.py` (la CI vérifie que le JSON est à jour).
- Sans secrets de signature, `assembleRelease` signe avec la clé de debug (pratique en local, jamais publié).

## Licences

Polices Fraunces et Outfit sous SIL Open Font License 1.1 (voir `app/src/main/assets/licenses`).
