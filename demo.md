# Scénario de démo — Projet Programmation Réseau

Durée : 8-10 minutes. L'objectif est de raconter une **histoire** au prof, pas juste taper des commandes.

> **Histoire** : *"Diane, Moussa et Awa, trois étudiants en M1 Informatique, veulent réviser les réseaux ensemble."*

---

## Préparation avant la démo (5 min avant)

Ouvre **5 terminaux** dans VS Code et place-les en grille pour que le prof voie tout.

- **T1** : Serveur central
- **T2** : Serveur de groupe (ta partie)
- **T3** : Cliente Diane
- **T4** : Client Moussa
- **T5** : Cliente Awa (pour démo archivage)

**Avant tout** — Tuer les anciens processus et recompiler :

```bash
killall java
javac Projet_revision/common/*.java Projet_revision/Group/*.java Projet_revision/Server/*.java Projet_revision/Client/*.java
```

**Si AirPlay bloque le port 5000** : Réglages système → AirDrop et Handoff → désactiver Récepteur AirPlay.

---

## Étape 1 — Démarrage de l'infrastructure (1 min)

**Tu dis** : *"Notre système repose sur 3 composants. D'abord, le serveur central qui sert d'annuaire des groupes."*

**T1 — Serveur central :**
```bash
java Projet_revision.Server.UniversityServer
```
→ Affiche : `=== Serveur Université démarré sur le port 5000 ===`

**Tu dis** : *"Maintenant on lance un groupe de révision pour les M1 Info en Réseaux. Au démarrage, il s'enregistre automatiquement auprès du serveur central via TCP."*

**T2 — Groupe :**
```bash
java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux
```
→ Côté groupe : `Groupe M1-INFO-Reseaux pret (TCP:6000, UDP:6001)`
→ Côté serveur central : `REGISTER M1-INFO-Reseaux ...` puis `200 REGISTER OK`

**Pointer la connexion REGISTER** : *"Vous voyez ici l'enregistrement automatique du groupe au serveur central via TCP."*

---

## Étape 2 — Connexion de Diane (1 min 30)

**Tu dis** : *"Diane, étudiante en M1 Informatique, lance son client."*

**T3 — Lancer le client :**
```bash
java Projet_revision.Client.StudentClient localhost 5000
```

→ Le client demande :
```
Votre nom     : Diane
Votre filiere : Informatique
Votre niveau  : M1
```

**Tape `Diane`, `Informatique`, `M1`** à chaque prompt.

→ Le menu des commandes s'affiche.

**Tu dis** : *"Diane veut voir les groupes disponibles pour son profil."*

```
list Informatique M1
```

→ Affiche `200 LIST 1 M1-INFO-Reseaux`

**Pointer** : *"Le client interroge le serveur central en TCP avec sa filière et son niveau. Le serveur lui renvoie la liste des groupes correspondants."*

**Démonstration de la commande `info`** :

```
info M1-INFO-Reseaux
```

→ Affiche `[INFO] M1-INFO-Reseaux -> IP : 192.168.1.x | Port : 6000`

**Pointer** : *"Avant de rejoindre un groupe, Diane peut consulter ses détails : son adresse IP et son port d'écoute."*

---

## Étape 3 — Rejoindre un groupe (1 min)

**Tu dis** : *"Diane décide de rejoindre ce groupe."*

```
join M1-INFO-Reseaux
```

→ Côté Diane : `[Groupe] 200 JOIN WELCOME Diane` puis `[INFO] Vous avez rejoint le groupe`
→ Côté groupe (T2) : `[MemberHandler] Diane a rejoint le groupe (UDP ...)`

**Pointer** : *"Diane récupère l'adresse du groupe via le serveur central, puis se connecte directement au serveur de groupe en TCP. Elle lui transmet son port UDP pour les diffusions et son port TCP privé pour les chats."*

---

## Étape 4 — Arrivée de Moussa (1 min)

**Tu dis** : *"Moussa arrive à son tour."*

**T4 — Lancer un 2ème client :**
```bash
java Projet_revision.Client.StudentClient localhost 5000
```

À chaque prompt : `Moussa`, `Informatique`, `M1`

Puis :
```
list Informatique M1
join M1-INFO-Reseaux
```

→ Côté groupe (T2) : `[MemberHandler] Moussa a rejoint le groupe...` et `[Broadcast] Membres actuels : 2`

**Pointer** : *"Le serveur de groupe gère plusieurs membres en parallèle grâce à un thread par membre."*

---

## Étape 5 — Discussion publique en UDP (2 min) — le moment fort

**Tu dis** : *"Diane veut saluer le groupe. Les messages publics sont diffusés en UDP, le mode adapté pour la diffusion à plusieurs."*

**Dans T3 (Diane) :**
```
say Salut Moussa, prêt pour réviser les sockets ?
```

→ Le message apparaît quasi instantanément chez Diane ET chez Moussa.

**Pointer** : *"Le message est envoyé en UDP au serveur de groupe sur le port 6001, qui le diffuse à tous les membres. Notez la rapidité : c'est l'avantage d'UDP pour la diffusion à plusieurs."*

**Dans T4 (Moussa) :**
```
say Carrément ! On commence par TCP ou UDP ?
```

→ Le message apparaît chez Diane.

---

## Étape 6 — Planification d'une session (1 min)

**Tu dis** : *"Ils décident de planifier une vraie session de révision."*

**Dans T3 (Diane) :**
```
plan 2026-05-16 14:00 Revision_TCP_UDP
```

→ Le PLAN apparaît chez Diane et Moussa avec un format spécial 📅

**Pointer** : *"Le PLAN est diffusé comme un message normal, mais en plus il est archivé par le serveur de groupe. Tout nouvel arrivant verra automatiquement les sessions planifiées."*

---

## Étape 7 — Démonstration de l'archivage (1 min) — le moment "wow"

**Tu dis** : *"Pour prouver l'archivage, on va simuler l'arrivée tardive d'une 3ème étudiante qui rejoint APRÈS la planification."*

**T5 — Lancer Awa :**
```bash
java Projet_revision.Client.StudentClient localhost 5000
```

À chaque prompt : `Awa`, `Informatique`, `M1`

Puis :
```
join M1-INFO-Reseaux
```

→ Awa voit s'afficher automatiquement les messages passés ET le PLAN planifié avant son arrivée !

**Pointer** : *"Vous voyez ? Awa n'était pas là quand Diane a planifié la session, mais elle la reçoit quand même en rejoignant. C'est l'archivage côté serveur de groupe : il garde l'historique des PLAN et les renvoie en TCP aux nouveaux arrivants."*

---

## Étape 8 — Discussion privée TCP (1 min)

**Tu dis** : *"Maintenant Diane veut envoyer un message privé à Moussa, en dehors du groupe."*

**Dans T3 (Diane) :**
```
hey Moussa
```

→ Diane : `[PrivateChat] Conversation privée ouverte avec Moussa`
→ Moussa : `[PrivateChat] Connexion entrante de ...`

Puis tape directement des messages dans le terminal de Diane (sans préfixe de commande) :
```
salut comment vas-tu
```

→ Moussa voit `[Diane] salut comment vas-tu`

Moussa peut répondre dans son terminal :
```
ça va bien et toi
```

→ Diane voit `[Moussa] ça va bien et toi`

**Pointer** : *"Le HEY passe par le serveur de groupe en TCP, qui renvoie l'adresse et le port TCP privé de Moussa. Diane se connecte alors directement à Moussa en TCP — c'est du peer-to-peer, le serveur n'intervient plus pendant la conversation."*

Pour quitter le chat privé :
```
bye
```

---

## Étape 9 — Conclusion (30 sec)

Dans chaque client, taper `quit`.

**Dis au prof** :

> *"Pour résumer notre système : trois composants qui communiquent via 4 modes différents :*
> *— TCP pour les requêtes au serveur central (LIST, INFO)*
> *— TCP pour rejoindre un groupe (JOIN) et la signalisation HEY*
> *— UDP pour la diffusion publique (MSG, PLAN)*
> *— TCP pour les chats privés en peer-to-peer*
> *Le tout gère plusieurs clients simultanés grâce aux threads, et archive les sessions planifiées pour les nouveaux arrivants."*

---

## Tableau récapitulatif des commandes (à imprimer ou à afficher pendant la démo)

| Étape | Terminal | Commande |
|---|---|---|
| Tuer ancien | n'importe | `killall java` |
| Compiler | n'importe | `javac Projet_revision/common/*.java Projet_revision/Group/*.java Projet_revision/Server/*.java Projet_revision/Client/*.java` |
| Lancer serveur | T1 | `java Projet_revision.Server.UniversityServer` |
| Lancer groupe | T2 | `java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux` |
| Lancer Diane | T3 | `java Projet_revision.Client.StudentClient localhost 5000` puis `Diane`, `Informatique`, `M1` |
| Diane liste | T3 | `list Informatique M1` |
| Diane info | T3 | `info M1-INFO-Reseaux` |
| Diane rejoint | T3 | `join M1-INFO-Reseaux` |
| Lancer Moussa | T4 | `java Projet_revision.Client.StudentClient localhost 5000` puis `Moussa`, `Informatique`, `M1` |
| Moussa liste | T4 | `list Informatique M1` |
| Moussa rejoint | T4 | `join M1-INFO-Reseaux` |
| Diane parle | T3 | `say Salut Moussa, prêt pour réviser les sockets ?` |
| Moussa répond | T4 | `say Carrément ! On commence par TCP ou UDP ?` |
| Diane planifie | T3 | `plan 2026-05-16 14:00 Revision_TCP_UDP` |
| Lancer Awa | T5 | `java Projet_revision.Client.StudentClient localhost 5000` puis `Awa`, `Informatique`, `M1` |
| Awa rejoint (voit l'archive !) | T5 | `join M1-INFO-Reseaux` |
| Chat privé Diane → Moussa | T3 | `hey Moussa` |
| Diane parle dans le chat | T3 | (taper directement, ex: `salut comment vas-tu`) |
| Moussa répond | T4 | (taper directement) |
| Quitter chat privé | T3 ou T4 | `bye` |
| Tout quitter | tous | `quit` |

---

## Valeurs exactes à utiliser

- Filière à taper : `Informatique`
- Niveau à taper : `M1`
- Nom de groupe : `M1-INFO-Reseaux`

Ces valeurs doivent être tapées **identiques** partout pour que le filtrage marche (la recherche est insensible à la casse côté serveur grâce à `equalsIgnoreCase`).

---

## Conseils pour la démo

### Anticipe les pannes
- Avant la démo, fais le scénario complet **une fois** seule pour vérifier que tout marche
- Garde un mode plan B : si quelque chose plante, dis *"on a un bug qu'on n'a pas eu le temps de corriger, mais voilà la suite"*
- **Toujours faire `killall java` avant** pour partir d'un état propre

### Ne tape pas trop vite
- Laisse 2-3 secondes entre chaque commande pour que le prof observe les effets dans les autres terminaux
- Si tu vas trop vite, tu perds l'effet pédagogique

### Phrases types à utiliser
- *"On voit ici que..."*
- *"Vous remarquez que..."*
- *"Ce qui est important c'est que..."*
- *"Cela illustre bien..."*

### En cas de question difficile
- *"On a fait ce choix parce que..."* (toujours justifier)
- *"Le sujet ne précisait pas, donc on a opté pour..."*
- *"On a privilégié X plutôt que Y pour des raisons de..."*

---

## Points clés à valoriser à l'oral

1. **Architecture en 3 modules** : serveur central, serveurs de groupes, clients
2. **4 modes de communication** : TCP × 3 (REGISTER, JOIN/HEY, chat privé) + UDP pour le broadcast
3. **Multi-clients simultanés** grâce aux threads (un thread par MemberHandler)
4. **Archivage des PLAN** pour les nouveaux arrivants avec marqueur de fin `--- END_ARCHIVE ---`
5. **Peer-to-peer TCP** pour les chats privés (le serveur ne fait que de l'annuaire ensuite)
6. **Thread-safety** avec `ConcurrentHashMap` (serveur central) et `CopyOnWriteArrayList` (serveur groupe)
7. **Package `common` partagé** entre tous les modules pour éviter les divergences de protocole