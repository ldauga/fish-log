---
description: Déchiffre un reçu de don, vérifie les doublons, et met à jour hall_of_fame.json
---

# add-donor — Ajouter un donateur au Hall of Fame

## Usage
```
/add-donor <chemin_vers_le_reçu>
```
Exemple : `/add-donor C:\Users\user\Downloads\receipt_20260618_153042.txt`

---

## Ce que doit faire ce skill

### 1. Lire le fichier reçu

Lire le contenu du fichier passé en argument (args). C'est une chaîne de la forme `FISHLOG_V1:xxxxxxxx...`.

### 2. Calculer le hash du reçu (anti-doublon)

Calculer le SHA-256 du contenu brut du fichier via PowerShell :

```powershell
$content = Get-Content "<fichier>" -Raw
$bytes = [System.Text.Encoding]::UTF8.GetBytes($content.Trim())
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hash = [BitConverter]::ToString($sha256.ComputeHash($bytes)).Replace("-","").ToLower()
```

### 3. Vérifier le fichier des reçus traités

Le fichier de tracking est : `C:\Users\user\.fishlog_processed.txt`
(hors repo, jamais pushé)

Chaque ligne = `HASH|DATE_TRAITEMENT|PLAYER|AMOUNT`

- Si le hash est déjà présent → **stopper et informer l'utilisateur que ce reçu a déjà été traité**, avec la date et le joueur associés.
- Si absent → continuer.

### 4. Déchiffrer le reçu

Via PowerShell avec la clé AES hardcodée :

```powershell
$key = [byte[]]@(0x4C,0x65,0x6F,0x52,0x69,0x76,0x65,0x72,0x46,0x69,0x73,0x68,0x4C,0x6F,0x67,0x4D,0x6F,0x64,0x32,0x30,0x32,0x36,0x21,0x40,0x23,0x24,0x25,0x5E,0x26,0x2A,0x28,0x29)

$raw = (Get-Content "<fichier>" -Raw).Trim()
$b64 = $raw.Substring("FISHLOG_V1:".Length)
$combined = [Convert]::FromBase64String($b64)
$iv        = $combined[0..15]
$encrypted = $combined[16..($combined.Length - 1)]

$aes = [System.Security.Cryptography.Aes]::Create()
$aes.Key     = $key
$aes.IV      = $iv
$aes.Mode    = [System.Security.Cryptography.CipherMode]::CBC
$aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7

$dec   = $aes.CreateDecryptor()
$plain = [System.Text.Encoding]::UTF8.GetString($dec.TransformFinalBlock($encrypted, 0, $encrypted.Length))
# $plain = "player|amount|date" ex: "Sam_Kawai|1000.00|2026-06-18 15:30:00"
```

Extraire les 3 champs en splittant sur `|`.

### 5. Mettre à jour `hall_of_fame.json`

Fichier : `C:\Users\user\fishlog-mod\src\main\resources\assets\fishlog\hall_of_fame.json`

Format attendu : tableau JSON `[{"player":"X","amount":100.0,"date":"2026-06-18"}, ...]`

- Si le joueur **n'existe pas** → ajouter une nouvelle entrée avec `amount` et `date` (format `yyyy-MM-dd` seulement).
- Si le joueur **existe déjà** → additionner le montant à son total existant. **Garder la date originale** (date de la première donation — ne pas mettre à jour).
- Retrier le tableau par `amount` décroissant après modification.
- Écrire le fichier avec une indentation de 2 espaces, proprement formaté.

### 6. Enregistrer le reçu dans le fichier de tracking

Ajouter une ligne dans `C:\Users\user\.fishlog_processed.txt` :
```
<hash>|<date_traitement yyyy-MM-dd>|<player>|<amount>
```

### 7. Résumé final

Afficher un résumé clair :
```
✅ Don ajouté :
   Joueur  : Sam_Kawai
   Montant : +1 000.00 (total : 1 500.00)
   Date    : 2026-06-18

hall_of_fame.json mis à jour — pense à faire un build et à copier dans Badlands.
```

---

## Contraintes

- Ne jamais modifier le fichier de tracking si la mise à jour du JSON a échoué.
- Si le déchiffrement échoue (format invalide, mauvaise clé) → informer clairement l'utilisateur.
- Le fichier `.fishlog_processed.txt` ne doit JAMAIS être ajouté au repo git.
