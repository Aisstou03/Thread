

> **Histoire** : *"Diane, Moussa et Awa, trois étudiants en M1 Info, veulent réviser les réseaux ensemble."*

---

## Préparation avant la démo (5 min avant)

Ouvre **6 terminaux** dans VS Code et place-les en grille pour que le prof voie tout.

- **T1** : Serveur central
- **T2** : Serveur de groupe (partie B)
- **T3** : Cliente Diane
- **T4** : Cliente Moussa
- **T5** : Cliente Awa (pour démo archivage)
- **T6** (optionnel) : un 2ème groupe pour montrer la diversité

**Avant tout** — Tuer les anciens processus et recompiler :

```bash
javac Projet_revision/common/*.java Projet_revision/Group/*.java Projet_revision/Server/*.java Projet_revision/Client/*.java
```

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

**Tu dis** : *"Diane, étudiante en M1 Info, lance son client."*

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

→ Affiche le menu des commandes disponibles.

**Tu dis** : *"Diane veut voir les groupes disponibles pour son profil."*

```
list
```

(sans argument — le client utilise automatiquement Informatique/M1 grâce à son profil)

→ Affiche `M1-INFO-Reseaux`

**Pointer** : *"Le client interroge le serveur central en TCP, qui filtre les groupes selon la filière et le niveau de l'étudiante."*

---

## Étape 3 — Rejoindre un groupe (1 min)

**Tu dis** : *"Diane décide de rejoindre ce groupe."*

```
join M1-INFO-Reseaux
```

→ Côté Diane : `[Groupe] 200 JOIN WELCOME Diane` puis `[INFO] Vous avez rejoint : M1-INFO-Reseaux`
→ Côté groupe (T2) : `[MemberHandler] Diane a rejoint le groupe (UDP ...)`

**Pointer** : *"Diane récupère d'abord les infos du groupe via le serveur central (TCP), puis se connecte directement au serveur de groupe en TCP avec son port UDP et son port TCP privé."*

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
list
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

**Pointer** : *"Le message est envoyé en UDP au serveur de groupe, qui le diffuse en broadcast UDP à tous les membres. Notez la rapidité : c'est l'avantage d'UDP pour la diffusion."*

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

→ Le PLAN apparaît chez Diane et Moussa avec un format spécial.

**Pointer** : *"Le PLAN est diffusé comme un message normal, mais en plus il est archivé par le serveur de groupe. Tout nouvel arrivant verra automatiquement les sessions planifiées."*

---

## Étape 7 — Démonstration de l'archivage (1 min) — le moment "wow"

**Tu dis** : *"Pour prouver l'archivage, on va simuler l'arrivée tardive d'une 3ème étudiante qui rejoint après la planification."*

**T5 — Lancer Awa :**
```bash
java Projet_revision.Client.StudentClient localhost 5000
```

À chaque prompt : `Awa`, `Informatique`, `M1`

Puis :
```
join M1-INFO-Reseaux
```

→ Awa voit s'afficher automatiquement le PLAN qui avait été planifié AVANT son arrivée !

**Pointer** : *"Vous voyez ? Awa n'était pas là quand Diane a planifié la session, mais elle la reçoit quand même en rejoignant. C'est l'archivage côté serveur de groupe : il garde l'historique des PLAN et les renvoie aux nouveaux arrivants en TCP."*

---

## Étape 8 — Discussion privée TCP (1 min, optionnel)

**Tu dis** : *"Maintenant Diane veut envoyer un message privé à Moussa, en dehors du groupe."*

**Dans T3 (Diane) :**
```
hey Moussa
```

→ Diane : `[PrivateChat] Conversation privée ouverte avec Moussa`
→ Moussa : `[PrivateChat] Connexion entrante de 192.168.1.16`

Puis Diane peut taper directement des messages :
```
salut comment vas-tu
```

→ Moussa voit `[192.168.1.16] salut comment vas-tu`

Moussa répond :
```
bien et toi
```

→ Diane voit `[Moussa] bien et toi`

**Pointer** : *"Le HEY passe par le serveur de groupe en TCP, qui renvoie l'adresse de Moussa avec son port TCP privé. Diane se connecte alors directement à Moussa en TCP — c'est du peer-to-peer, le serveur n'intervient plus."*

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
> *— TCP pour rejoindre un groupe (JOIN)*
> *— UDP pour la diffusion publique (MSG, PLAN)*
> *— TCP pour les chats privés (HEY)*
> *Le tout gère plusieurs clients simultanés grâce aux threads, et archive les sessions planifiées pour les nouveaux arrivants."*

---

## Tableau récapitulatif des commandes (à imprimer)

| Étape | Terminal | Commande |
|---|---|---|
| Tuer ancien | n'importe | `killall java` |
| Compiler | n'importe | `javac Projet_revision/common/*.java Projet_revision/Group/*.java Projet_revision/Server/*.java Projet_revision/Client/*.java` |
| Lancer serveur | T1 | `java Projet_revision.Server.UniversityServer` |
| Lancer groupe | T2 | `java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux` |
| Lancer Diane | T3 | `java Projet_revision.Client.StudentClient localhost 5000` → puis taper `Diane`, `Informatique`, `M1` |
| Diane liste | T3 | `list` |
| Diane rejoint | T3 | `join M1-INFO-Reseaux` |
| Lancer Moussa | T4 | `java Projet_revision.Client.StudentClient localhost 5000` → puis `Moussa`, `Informatique`, `M1` |
| Moussa rejoint | T4 | `join M1-INFO-Reseaux` |
| Diane parle | T3 | `say Salut Moussa, prêt pour réviser ?` |
| Moussa répond | T4 | `say Carrément ! On commence par TCP ou UDP ?` |
| Diane planifie | T3 | `plan 2026-05-16 14:00 Revision_TCP_UDP` |
| Lancer Awa (test archivage) | T5 | `java Projet_revision.Client.StudentClient localhost 5000` → puis `Awa`, `Informatique`, `M1` |
| Awa rejoint (et voit l'archive !) | T5 | `join M1-INFO-Reseaux` |
| Chat privé (optionnel) | T3 | `hey Moussa` |
| Quitter chat privé | T3 | `bye` |
| Tout quitter | tous | `quit` |

---

## Conseils pour la démo

### Anticipe les pannes
- Avant la démo, fais le scénario complet **une fois** seule pour vérifier que tout marche
- Garde un mode plan B : si quelque chose plante, dis *"on a un bug qu'on n'a pas eu le temps de corriger, mais voilà la suite"*
- **Toujours faire `killall java` avant** pour partir d'un état propre

### Ne tape pas trop vite
- Laisse 2-3 secondes entre chaque commande pour que le prof observe les effets dans les autres terminaux
- Si tu vas trop vite, tu perds l'effet pédagogique

### Phrases types
- *"On voit ici que..."*
- *"Vous remarquez que..."*
- *"Ce qui est important c'est que..."*

### Si AirPlay bloque le port 5000
- Réglages système → AirDrop et Handoff → désactiver Récepteur AirPlay

---

## Points clés à valoriser à l'oral

1. **Architecture 3 modules** : serveur central, serveurs de groupes, clients
2. **4 modes de communication** : TCP × 3 + UDP pour le broadcast
3. **Multi-clients simultanés** grâce aux threads
4. **Archivage des PLAN** pour les nouveaux arrivants
5. **Peer-to-peer TCP** pour les chats privés (le serveur ne fait plus que de l'annuaire)
6. **Filtrage automatique** des groupes selon le profil de l'étudiant