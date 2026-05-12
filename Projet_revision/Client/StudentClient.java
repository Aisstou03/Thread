package Projet_revision.Client;

import Projet_revision.common.Protocol;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudentClient {

    private static final Scanner KBD = new Scanner(System.in);

    private static final String HELP = """

            === COMMANDES PROTOCOLE ===
            LIST <filiere> <niveau>         — Demander les groupes disponibles
            INFO <nom_groupe>               — Obtenir les details d'un groupe
            JOIN <nom_etudiant>             — Rejoindre un groupe de revision
            MSG  <nom_etudiant> <message>   — Envoyer un message public (UDP)
            HEY  <source> TO <destination>  — Discussion privee (TCP)
            PLAN <nom> <date> <h> <desc>    — Proposer une session de revision
            QUIT                            — Quitter l'application
            """;

    private String studentName;
    private String filiere;
    private String niveau;

    private final String serverHost;
    private final int    serverPort;
    private ServerConnection serverConn;

    private GroupListener      groupListener;
    private PrivateChatHandler privateChatHandler;

    private String currentGroup = null;

    public StudentClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    private void saisirProfil() {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║    Systeme de revision — Identification      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.print("Votre nom     : ");
        studentName = KBD.nextLine().trim();
        System.out.print("Votre filiere : ");
        filiere = KBD.nextLine().trim();
        System.out.print("Votre niveau  : ");
        niveau = KBD.nextLine().trim();
        System.out.println("[INFO] Profil : " + studentName + " | " + filiere + " | " + niveau + "\n");
    }

    public void start() {
        saisirProfil();

        serverConn = new ServerConnection(serverHost, serverPort);
        if (!serverConn.connect()) {
            System.err.println("[ERREUR] Impossible de joindre le serveur central " + serverHost + ":" + serverPort);
            return;
        }
        System.out.println("[INFO] Connecte au serveur central " + serverHost + ":" + serverPort);

        groupListener = new GroupListener();
        int udpPort = groupListener.getLocalPort();
        if (udpPort < 0) {
            System.err.println("[ERREUR] Impossible d'ouvrir le socket UDP.");
            serverConn.close();
            return;
        }
        System.out.println("[INFO] Ecoute UDP sur le port " + udpPort);

        privateChatHandler = new PrivateChatHandler(studentName);
        int privatePort = privateChatHandler.getLocalPort();
        if (privatePort < 0) {
            System.err.println("[ERREUR] Impossible d'ouvrir le port TCP prive.");
            serverConn.close();
            return;
        }
        System.out.println("[INFO] Chat prive TCP en ecoute sur le port " + privatePort);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(groupListener);
        pool.submit(privateChatHandler.receiverTask());

        System.out.println(HELP);

        while (KBD.hasNextLine()) {
            System.out.print(studentName + "@" + (currentGroup != null ? currentGroup : "~") + " > ");
            String line = KBD.nextLine().trim();
            if (line.isEmpty()) continue;
            handleCommand(line, udpPort);
        }

        pool.shutdownNow();
        groupListener.stop();
        privateChatHandler.stop();
        serverConn.close();
        System.out.println("[INFO] Deconnecte. A bientot !");
    }

    private void handleCommand(String line, int udpPort) {
        String[] parts = line.split("\\s+", 2);
        String cmd  = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {

            case "list" -> {
                String fil = filiere;
                String niv = niveau;
                if (!args.isEmpty()) {
                    String[] a = args.split("\\s+");
                    if (a.length < 2) {
                        System.out.println("[USAGE] list [<filiere> <niveau>]");
                        return;
                    }
                    fil = a[0];
                    niv = a[1];
                }
                System.out.println("[ENVOI] LIST " + fil + " " + niv);
                String response = serverConn.list(fil, niv);
                if (response == null) {
                    System.out.println("[ERREUR] Pas de reponse du serveur.");
                    return;
                }
                System.out.println("[RECU] " + response);
                if (response.startsWith(Protocol.CODE_OK + " LIST")) {
                    String[] tok = response.split("\\s+", 4);
                    int nb = Integer.parseInt(tok[2]);
                    if (nb == 0) {
                        System.out.println("[INFO] Aucun groupe pour " + fil + " / " + niv + ".");
                    } else {
                        System.out.println("[INFO] " + nb + " groupe(s) :");
                        for (String g : tok[3].split(",")) {
                            System.out.println("       -> " + g.trim());
                        }
                    }
                }
            }

            case "info" -> {
                if (args.isEmpty()) {
                    System.out.println("[USAGE] info <nom_groupe>");
                    return;
                }
                System.out.println("[ENVOI] INFO " + args);
                String[] info = serverConn.info(args);
                if (info == null) {
                    System.out.println("[ERREUR] 404 — Groupe introuvable : " + args);
                } else {
                    System.out.println("[INFO] " + args + " -> IP : " + info[0] + " | Port : " + info[1]);
                }
            }

            case "join" -> {
                if (args.isEmpty()) {
                    System.out.println("[USAGE] join <nom_groupe>");
                    return;
                }
                System.out.println("[ENVOI] INFO " + args);
                String[] info = serverConn.info(args);
                if (info == null) {
                    System.out.println("[ERREUR] 404 — Groupe introuvable : " + args);
                    return;
                }
                String groupIp   = info[0];
                int    groupPort = Integer.parseInt(info[1]);
                boolean joined = groupListener.joinGroup(groupIp, groupPort, studentName, udpPort);
                if (joined) {
                    currentGroup = args;
                    System.out.println("[INFO] Vous avez rejoint : " + args);
                    System.out.println("[INFO] Tapez 'say <msg>' pour parler au groupe.");
                } else {
                    System.out.println("[ERREUR] Impossible de rejoindre " + args);
                }
            }

            case "say" -> {
                if (currentGroup == null) {
                    System.out.println("[INFO] Rejoignez d'abord un groupe : join <groupe>");
                    return;
                }
                if (args.isEmpty()) {
                    System.out.println("[USAGE] say <message>");
                    return;
                }
                groupListener.sendMessage(Protocol.CMD_MSG, studentName, args);
            }

            case "hey" -> {
                if (args.isEmpty()) {
                    System.out.println("[USAGE] hey <nom_etudiant>");
                    return;
                }
                if (currentGroup == null) {
                    System.out.println("[INFO] Rejoignez d'abord un groupe pour localiser l'etudiant.");
                    return;
                }
                System.out.println("[INFO] Demande de chat prive avec " + args + "...");
                privateChatHandler.initiate(groupListener.getGroupTcpSocket(), studentName, args);
            }

            case "plan" -> {
                if (currentGroup == null) {
                    System.out.println("[INFO] Rejoignez d'abord un groupe : join <groupe>");
                    return;
                }
                if (args.isEmpty()) {
                    System.out.println("[USAGE] plan <date> <heure> <description>");
                    System.out.println("[EX]   plan 2026-05-02 14:00 Revision_reseaux");
                    return;
                }
                String[] p = args.split("\\s+", 3);
                if (p.length < 3) {
                    System.out.println("[USAGE] plan <date> <heure> <description>");
                    return;
                }
                String date  = p[0];
                String heure = p[1];
                String desc  = p[2].replace(" ", "_");
                groupListener.sendMessage(Protocol.CMD_PLAN, studentName, date + " " + heure + " " + desc);
                System.out.println("[INFO] Session proposee : " + date + " a " + heure + " — " + desc.replace("_", " "));
            }

            case "help" -> System.out.println(HELP);

            case "quit", "exit" -> {
                System.out.println("[INFO] Fermeture...");
                System.exit(0);
            }

            default -> System.out.println("[?] Commande inconnue : '" + cmd + "'. Tapez 'help' pour l'aide.");
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage   : java Projet_revision.Client.StudentClient <serverHost> <serverPort>");
            System.out.println("Exemple : java Projet_revision.Client.StudentClient localhost 9000");
            System.exit(1);
        }
        new StudentClient(args[0], Integer.parseInt(args[1])).start();
    }
}