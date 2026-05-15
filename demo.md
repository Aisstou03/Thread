# ============================================================
#  PROJET PROGRAMMATION RÉSEAU — Commandes de test

# ============================================================

# --- PRÉPARATION ---
killall java   # Tuer les anciens processus pour partir propre

# Compiler tout le projet
javac Projet_revision/common/*.java \
      Projet_revision/Group/*.java \
      Projet_revision/Server/*.java \
      Projet_revision/Client/*.java


# --- T1 : Serveur central (annuaire des groupes) ---
java Projet_revision.Server.UniversityServer
# Attendu : "=== Serveur Université démarré sur le port 5000 ==="


# --- T2 : Serveur de groupe (s'enregistre automatiquement auprès du serveur central via TCP) ---
java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux
# Attendu T2 : "Groupe M1-INFO-Reseaux pret (TCP:6000, UDP:6001)"
# Attendu T1 : "REGISTER M1-INFO-Reseaux ..." puis "200 REGISTER OK"


# --- T3 : Cliente Diane ---
java Projet_revision.Client.StudentClient localhost 5000
# Saisir quand demandé :
#   Nom     → Diane
#   Filière → Informatique
#   Niveau  → M1

list Informatique M1       # Interroge le serveur central en TCP → renvoie les groupes filtrés
# Attendu : "200 LIST 1 M1-INFO-Reseaux"

info M1-INFO-Reseaux       # Récupère l'IP et le port TCP du groupe avant de rejoindre
# Attendu : "[INFO] info  M1-INFO-Reseaux
[INFO] Groupe : M1-INFO-Reseaux
[INFO]   IP       : 192.168.1.99
[INFO]   Port     : 6002
[INFO]   Filière  : INFO
[INFO]   Niveau   : M1
"

join M1-INFO-Reseaux       # Connexion TCP au groupe + envoi du port UDP et du port TCP privé
# Attendu T3 : "200 JOIN WELCOME Diane"
# Attendu T2 : "[MemberHandler] Diane a rejoint le groupe" → 1 thread créé côté serveur

Pour obtenir tous les groupes, utilise le joker * dans la commande list :

list * *

Tu peux aussi faire :

list Informatique * pour tous les groupes de la filière Informatique
list * M1 pour tous les groupes de niveau M1


# --- T4 : Client Moussa (même procédure) ---
java Projet_revision.Client.StudentClient localhost 5000
# Saisir : Moussa / Informatique / M1

list Informatique M1
join M1-INFO-Reseaux
# Attendu T2 : "[MemberHandler] Moussa a rejoint" + "[Broadcast] Membres actuels : 2"
# → 2 threads MemberHandler tournent en parallèle


# --- T3 : Diane envoie un message public (broadcast UDP) ---
say Salut Moussa, prêt pour réviser les sockets ?
# Le message arrive quasi instantanément chez Diane ET chez Moussa via UDP (port 6001)

# --- T4 : Moussa répond (broadcast UDP) ---
say Carrément ! On commence par TCP ou UDP ?
# Diane le reçoit immédiatement


# --- T3 : Diane planifie une session (UDP + archivage serveur) ---
plan 2026-05-16 14:00 Revision_TCP_UDP
# Le PLAN est diffusé comme un message normal (UDP)
# ET archivé côté serveur pour les futurs membres


# --- T5 : Cliente Awa — arrive APRÈS la planification (test archivage) ---
java Projet_revision.Client.StudentClient localhost 5000
# Saisir : Awa / Informatique / M1

join M1-INFO-Reseaux
# Attendu : Awa reçoit automatiquement le PLAN de Diane en TCP dès son arrivée
# → le serveur renvoie l'historique aux nouveaux membres jusqu'à "--- END_ARCHIVE ---"


# --- T3 : Chat privé Diane → Moussa (TCP peer-to-peer) ---
hey Moussa
# Le serveur de groupe renvoie l'adresse TCP privée de Moussa
# Diane se connecte ensuite DIRECTEMENT à Moussa (le serveur n'intervient plus)

salut, on révise ce soir ?    # Taper directement, sans préfixe de commande
# Attendu T4 : "[Diane] salut, on révise ce soir ?"

# --- T4 : Moussa répond dans le chat privé ---
oui, 20h ça te va ?
# Attendu T3 : "[Moussa] oui, 20h ça te va ?"

bye    # Fermer le chat privé (T3 ou T4)


# --- FIN : quitter proprement ---
quit   # À taper dans chaque terminal client (T3, T4, T5)

