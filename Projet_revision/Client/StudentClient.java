package Projet_revision.Client;

import Projet_revision.common.Protocol;

import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Point d'entrée du client étudiant.
 * Lance 3 threads en parallèle :
 *   1. GroupListener   — écoute les datagrammes UDP du groupe
 *   2. PrivateChatHandler (mode récepteur) — écoute les connexions TCP entrantes (HEY)
 *   3. Thread principal — lit les commandes clavier
 *
 * Usage : java client.StudentClient <serverHost> <serverPort>
 */
public class StudentClient {

    private static final Scanner KBD = new Scanner(System.in);

    // ── commandes disponibles ────────────────────────────────────────────────
    private static final String HELP =
        "Commandes disponibles :\n" +
        "  list <filiere> <niveau>   — liste les groupes\n" +
        "  join <groupe>             — rejoindre un groupe\n" +
        "  say <message>             — envoyer un message public (UDP)\n" +
        "  plan <message>            — envoyer une annonce (UDP archivé)\n" +
        "  hey <etudiant>            — démarrer un chat privé (TCP)\n" +
        "  quit                      — quitter\n";

    // ── état partagé ─────────────────────────────────────────────────────────
    private final String serverHost;
    private final int    serverPort;
    private final String studentName;

    private ServerConnection   serverConn;
    private GroupListener      groupListener;
    private PrivateChatHandler privateChatHandler;
    private PrivateChatHandler.ChatSession privateChatSession;

    // groupe courant
    private String currentGroup = null;

    public StudentClient(String serverHost, int serverPort, String studentName) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.studentName = studentName;
    }



    public void start() {
        // 1. Connexion au serveur central
        serverConn = new ServerConnection(serverHost, serverPort);
        if (!serverConn.connect()) {
            System.err.println("[ERREUR] Impossible de contacter le serveur central.");
            return;
        }
        System.out.println("[INFO] Connecté au serveur central " + serverHost + ":" + serverPort);

        // 2. GroupListener UDP (port aléatoire libre)
        groupListener = new GroupListener();
        int udpPort = groupListener.getLocalPort();
        System.out.println("[INFO] Écoute UDP sur le port " + udpPort);

        // 3. PrivateChatHandler — récepteur de HEY entrants
        privateChatHandler = new PrivateChatHandler(studentName);
        privateChatHandler.setSessionListener(new PrivateChatHandler.SessionListener() {
            @Override
            public void onSessionStarted(PrivateChatHandler.ChatSession session, String peerName, boolean initiator) {
                privateChatSession = session;
            }

            @Override
            public void onSessionEnded(String peerName) {
                if (privateChatSession != null && privateChatSession.getPeerName().equals(peerName)) {
                    privateChatSession = null;
                }
                System.out.println("[PrivateChat] Session terminée avec " + peerName + ".");
            }
        });
        int privatePort = privateChatHandler.getLocalPort();
        System.out.println("[INFO] Chat privé TCP sur le port " + privatePort);

        // 4. Lancer les threads de fond
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(groupListener);
        pool.submit(privateChatHandler.receiverTask());

        // 5. Boucle principale (thread courant)
        System.out.println(HELP);
        while (KBD.hasNextLine()) {
            String line = KBD.nextLine().trim();
            if (line.isEmpty()) continue;
            handleCommand(line, udpPort);
        }

        // 6. Nettoyage
        pool.shutdownNow();
        serverConn.close();
        System.out.println("[INFO] Déconnecté. À bientôt !");
    }

    // ── dispatch des commandes ────────────────────────────────────────────────
    private void handleCommand(String line, int udpPort) {
        if (privateChatSession != null) {
            if (line.equalsIgnoreCase("bye")) {
                String peerName = privateChatSession.getPeerName();
                privateChatSession.send("bye");
                privateChatSession.close();
                privateChatSession = null;
                System.out.println("[PrivateChat] Session terminée avec " + peerName + ".");
                return;
            }
            privateChatSession.send(line);
            return;
        }

        String[] parts = line.split("\\s+", 2);
        String cmd  = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {

            // ── LIST <filiere> <niveau> ───────────────────────────────────────
            case "list": {
                String[] a = args.split("\\s+");
                if (a.length < 2) {
                    System.out.println("[USAGE] list <filiere> <niveau>");
                    break;
                }
                String response = serverConn.list(a[0], a[1]);
                System.out.println(response != null ? response : "[ERREUR] Pas de réponse du serveur.");
                break;
            }

            // ── JOIN <groupe> ─────────────────────────────────────────────────
            case "join": {
                if (args.isEmpty()) {
                    System.out.println("[USAGE] join <groupe>");
                    break;
                }
                String groupName = args.trim();

                // Récupérer l'ip/port du groupe via INFO
                String[] info = serverConn.info(groupName);
                if (info == null) {
                    System.out.println("[ERREUR] Groupe introuvable : " + groupName);
                    break;
                }
                // info = { ip, port }
                String groupIp   = info[0];
                int    groupPort = Integer.parseInt(info[1]);
                boolean joined = groupListener.joinGroup(groupIp, groupPort, studentName, udpPort, privateChatHandler.getLocalPort());
                if (joined) {
                    currentGroup = groupName;
                    System.out.println("[INFO] Vous avez rejoint le groupe : " + groupName);
                } else {
                    System.out.println("[ERREUR] Impossible de rejoindre " + groupName);
                }
                break;
            }

            // ── SAY <message> ─────────────────────────────────────────────────
            case "say": {
                if (currentGroup == null) {
                    System.out.println("[INFO] Rejoignez d'abord un groupe avec 'join'.");
                    break;
                }
                if (args.isEmpty()) {
                    System.out.println("[USAGE] say <message>");
                    break;
                }
                groupListener.sendMessage(Protocol.CMD_MSG, studentName, args);
                break;
            }

            // ── PLAN <message> ────────────────────────────────────────────────
            case "plan": {
                if (currentGroup == null) {
                    System.out.println("[INFO] Rejoignez d'abord un groupe avec 'join'.");
                    break;
                }
                if (args.isEmpty()) {
                    System.out.println("[USAGE] plan <message>");
                    break;
                }
                groupListener.sendMessage(Protocol.CMD_PLAN, studentName, args);
                break;
            }

            // ── HEY <etudiant> ────────────────────────────────────────────────
            case "hey": {
                if (args.isEmpty()) {
                    System.out.println("[USAGE] hey <etudiant>");
                    break;
                }
                String target = args.trim();
                // Le serveur de groupe nous renvoie 300 HEY <ip> <port>
                PrivateChatHandler.ChatSession session = privateChatHandler.initiate(
                        groupListener.getGroupOut(),
                        groupListener.getGroupIn(),
                        studentName,
                        target
                );
                if (session != null) {
                    privateChatSession = session;
                }
                break;
            }

            // ── QUIT ──────────────────────────────────────────────────────────
            case "quit":
                System.out.println("[INFO] Fermeture...");
                System.exit(0);
                break;

            default:
                System.out.println("[?] Commande inconnue. Tapez 'help' pour l'aide.");
                break;

            case "help":
                System.out.println(HELP);
                break;
        }
    }

    // ── main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage : java client.StudentClient <serverHost> <serverPort>");
            System.exit(1);
        }
        String host = args[0];
        int    port = Integer.parseInt(args[1]);

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║    Systeme de revision — Identification      ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        System.out.print("Votre nom     : ");
        String name = KBD.nextLine().trim();
        System.out.print("Votre filiere : ");
        String filiere = KBD.nextLine().trim();
        System.out.print("Votre niveau  : ");
        String niveau = KBD.nextLine().trim();

        System.out.println("[INFO] Profil : " + name + " | " + filiere + " | " + niveau);

        new StudentClient(host, port, name).start();
    }
}
