
## Scénario de démo — durée environ 8-10 minutes

L'objectif : raconter une **histoire** au prof, pas juste taper des commandes. Les commandes prennent vie quand elles racontent quelque chose.

L'histoire : *"Diane et Moussa, deux étudiants en M1 Info, veulent réviser les réseaux ensemble"*

---

### Préparation avant la démo (5 min avant)

Ouvre **4 terminaux** dans VS Code et place-les en grille (2x2) pour que le prof voie tout en même temps. Numérote-les mentalement :

- **Terminal 1** (en haut à gauche) : Serveur central
- **Terminal 2** (en haut à droite) : Serveur de groupe (TA partie)
- **Terminal 3** (en bas à gauche) : Diane (1er étudiant)
- **Terminal 4** (en bas à droite) : Moussa (2ème étudiant)

Dans chaque terminal, sois déjà positionnée dans le bon dossier (`cd ...Thread`).

Compile une dernière fois avant la démo :
```bash
javac Projet_revision/common/*.java Projet_revision/Group/*.java Projet_revision/Server/*.java Projet_revision/Client/*.java
```

---

### Étape 1 — Démarrage de l'infrastructure (1 min)

**Ce que tu dis :** *"Notre système repose sur 3 composants. D'abord, le serveur central qui sert d'annuaire."*

**Terminal 1 :**
```bash
java Projet_revision.Server.UniversityServer
```

→ Le prof voit `=== Serveur Université démarré sur le port 5000 ===`

**Ce que tu dis :** *"Maintenant on lance un groupe de révision pour les M1 Info en Réseaux. Au démarrage, il s'enregistre automatiquement auprès du serveur central."*

**Terminal 2 :**
```bash
java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux
```

→ Le prof voit :
- Côté groupe : `Enregistrement aupres du serveur central` puis `Groupe pret`
- Côté serveur central : `REGISTER M1-INFO-Reseaux Informatique M1 Reseaux` puis `200 REGISTER OK`

**Pointe la connexion REGISTER** au prof : *"Vous voyez ici l'enregistrement automatique du groupe au serveur central, via TCP."*

---

### Étape 2 — Connexion du premier étudiant (1 min)

**Ce que tu dis :** *"Maintenant Diane, étudiante en M1 Info, lance son client pour rejoindre la plateforme."*

**Terminal 3 :**
```bash
java Projet_revision.Client.StudentClient localhost 5000 Diane Informatique M1
```

(Adapter selon que vous avez fait la modif des arguments ou non.)

→ Le prof voit le menu d'aide s'afficher.

**Ce que tu dis :** *"Diane veut voir les groupes disponibles pour son profil."*

Dans Diane :
```
list Informatique M1
```

→ Affiche `M1-INFO-Reseaux`.

**Pointe** : *"Le client interroge le serveur central en TCP, qui filtre les groupes selon la filière et le niveau."*

---

### Étape 3 — Rejoindre un groupe (1 min)

**Ce que tu dis :** *"Diane décide de rejoindre le groupe."*

Dans Diane :
```
join M1-INFO-Reseaux
```

→ Affiche `Vous avez rejoint le groupe : M1-INFO-Reseaux`
→ Côté serveur de groupe : `Diane a rejoint le groupe (UDP ...)`

**Pointe** : *"Diane récupère d'abord les infos du groupe via le serveur central (TCP), puis se connecte directement au serveur de groupe en TCP avec son port UDP pour les diffusions."*

---

### Étape 4 — Arrivée du 2ème étudiant (1 min)

**Ce que tu dis :** *"Moussa arrive à son tour."*

**Terminal 4 :**
```bash
java Projet_revision.Client.StudentClient localhost 5000 Moussa Informatique M1
```

Puis dans Moussa :
```
list Informatique M1
join M1-INFO-Reseaux
```

→ Côté serveur de groupe : `Moussa a rejoint le groupe`

**Pointe** : *"Le serveur de groupe gère plusieurs membres en parallèle grâce à un thread par membre."*

---

### Étape 5 — Discussion publique (le moment fort, 2 min)

**Ce que tu dis :** *"Diane veut saluer le groupe."*

Dans Diane :
```
say Salut Moussa, prêt pour réviser les sockets ?
```

→ Le message apparaît dans **Diane elle-même ET Moussa** quasi instantanément.

**Pointe** : *"Le message est envoyé en UDP au serveur de groupe, qui le diffuse en broadcast UDP à tous les membres. C'est le mode adapté pour les messages publics : rapide, efficace pour la diffusion à plusieurs."*

Dans Moussa :
```
say Carrément ! On commence par TCP ou UDP ?
```

→ Le message apparaît chez Diane.

---

### Étape 6 — Planification d'une session (1 min)

**Ce que tu dis :** *"Ils décident de planifier une vraie session de révision."*

Dans Diane :
```
plan 2026-05-16 14:00 Revision_TCP_UDP
```

→ Apparaît chez Moussa et chez Diane

**Pointe** : *"Le PLAN est diffusé comme un message normal, mais en plus il est archivé par le serveur de groupe. Tout nouvel arrivant verra automatiquement les sessions planifiées."*

---

### Étape 7 — Démontrer l'archivage (LE moment "wow", 1 min)

**Ce que tu dis :** *"Pour prouver l'archivage, on va simuler l'arrivée tardive d'un 3ème étudiant qui rejoint après la planification."*

**Ouvre un 5ème terminal**, et lance :
```bash
java Projet_revision.Client.StudentClient localhost 5000 Awa Informatique M1
```

Dans Awa :
```
join M1-INFO-Reseaux
```

→ **Awa voit s'afficher automatiquement le PLAN planifié plus tôt**, alors qu'elle n'était pas encore connectée à ce moment-là.

**Pointe** : *"Vous voyez ? Awa n'était pas là quand Diane a planifié la session, mais elle la reçoit quand même en rejoignant. C'est l'archivage côté serveur de groupe."*

C'est un effet impressionnant qui montre une vraie compréhension du sujet.

---

### Étape 8 — Discussion privée (1 min, optionnel)

**Ce que tu dis :** *"Diane veut envoyer un message privé à Moussa."*

Dans Diane :
```
hey Moussa
```

→ Une connexion TCP privée s'ouvre entre les deux clients

**Pointe** : *"Le HEY passe par le serveur de groupe (ou central, selon votre implémentation), qui renvoie l'adresse de Moussa. Diane se connecte alors directement à Moussa en TCP — c'est du peer-to-peer."*

⚠️ Ne fais ce test **que si tu sais qu'il marche**. Sinon, saute-le ou dis : *"Le chat privé est implémenté mais on n'a pas eu le temps de le démontrer."*

---

### Étape 9 — Conclusion (30 sec)

Dans chaque client, tape `quit`. Puis dis au prof :

> *"Pour résumer : nous avons un système avec trois composants qui communiquent via 4 modes différents : TCP pour les requêtes au serveur, TCP pour rejoindre un groupe, UDP pour la diffusion publique, et TCP pour les chats privés. Le tout gère plusieurs clients simultanés grâce aux threads."*

---

## Conseils pour la démo

### Anticipe les pannes
- **Avant** la démo, fais le scénario complet **une fois** seule pour vérifier que tout marche
- **Garde un mode plan B** : si quelque chose plante, dis *"on a un bug qu'on n'a pas eu le temps de corriger sur tel point, mais voilà la suite"*
- **Tue tous les processus Java avant** : `killall java` pour partir d'un état propre

### Tape pas trop vite
- Le prof veut **voir** ce qui se passe. Laisse 2-3 secondes entre chaque commande pour qu'il observe les effets dans les autres terminaux
- Si tu vas trop vite, tu perds l'effet pédagogique

### Garde un cheat sheet
- Imprime ou affiche les commandes sur une feuille à côté
- Tu seras stressée, c'est normal — pas de honte à avoir une antisèche

### Prépare 2-3 phrases types
- *"On voit ici que..."*
- *"Vous remarquez que..."*
- *"Ce qui est important c'est que..."*

Ça donne un côté **présentation pro** plutôt que **bidouillage**.

## Tableau récapitulatif des commandes

Voici un tableau de référence à imprimer pour le jour J :

| Étape | Terminal | Commande |
|---|---|---|
| Lancer serveur | T1 | `java Projet_revision.Server.UniversityServer` |
| Lancer groupe | T2 | `java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux` |
| Lancer Diane | T3 | `java Projet_revision.Client.StudentClient localhost 5000 Diane Informatique M1` |
| Diane voit les groupes | T3 | `list Informatique M1` |
| Diane rejoint | T3 | `join M1-INFO-Reseaux` |
| Lancer Moussa | T4 | `java Projet_revision.Client.StudentClient localhost 5000 Moussa Informatique M1` |
| Moussa rejoint | T4 | `join M1-INFO-Reseaux` |
| Diane parle | T3 | `say Salut Moussa, prêt pour réviser ?` |
| Moussa répond | T4 | `say Carrément !` |
| Diane planifie | T3 | `plan 2026-05-16 14:00 Revision_TCP_UDP` |
| Lancer Awa (test archivage) | T5 | `java Projet_revision.Client.StudentClient localhost 5000 Awa Informatique M1` |
| Awa rejoint (et voit l'archive !) | T5 | `join M1-INFO-Reseaux` |
| Chat privé (optionnel) | T3 | `hey Moussa` |

Tu peux mémoriser ce tableau dans un fichier `demo.md` à la racine de ton repo, comme ça tu l'as toujours sous la main.

---

Bonne nuit pour de vrai maintenant 😴 — demain on attaque l'explication de ton code pour que tu sois prête à répondre aux questions du prof pendant et après la démo.