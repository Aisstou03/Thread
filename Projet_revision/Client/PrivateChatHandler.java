package Projet_revision.Client;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gère les conversations privées via la commande HEY.
 *
 * Deux modes :
 *
 *   Initiateur  : envoie "HEY <moi> TO <destinataire>" au serveur de groupe
 *                 → reçoit "300 HEY <destinataire> <ip> <port>"
 *                 → ouvre une socket TCP vers le destinataire
 *                 → lance une boucle de chat dans un thread
 *
 *   Récepteur   : écoute en permanence sur un ServerSocket TCP
 *                 → accepte les connexions entrantes
 *                 → affiche les messages et répond (thread par conversation)
 */
public class PrivateChatHandler {

    private final ServerSocket      serverSocket;   // écoute les HEY entrants
    private final ExecutorService   chatPool;
    private static final Scanner    KBD = new Scanner(System.in);

    // ── constructeur ──────────────────────────────────────────────────────────
    public PrivateChatHandler(String myName) {
        this.chatPool = Executors.newCachedThreadPool();

        ServerSocket tmp = null;
        try {
            tmp = new ServerSocket(0); // port libre
        } catch (IOException e) {
            System.err.println("[PrivateChatHandler] Impossible d'ouvrir le ServerSocket : " + e.getMessage());
        }
        serverSocket = tmp;
    }

    // ── port local (à communiquer au serveur de groupe si nécessaire) ─────────
    public int getLocalPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    // ── tâche récepteur (à soumettre à un ExecutorService) ───────────────────
    /**
     * Retourne un Runnable qui boucle indéfiniment en acceptant les connexions HEY entrantes.
     * Chaque connexion est traitée dans un thread dédié.
     */
    public Runnable receiverTask() {
        return () -> {
            if (serverSocket == null) return;
            System.out.println("[PrivateChat] En attente de HEY entrants sur le port "
                    + serverSocket.getLocalPort());
            while (!serverSocket.isClosed()) {
                try {
                    Socket incoming = serverSocket.accept();
                    chatPool.submit(() -> handleIncomingChat(incoming));
                } catch (SocketException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("[PrivateChat] Erreur accept : " + e.getMessage());
                    }
                } catch (IOException e) {
                    System.err.println("[PrivateChat] Erreur accept : " + e.getMessage());
                }
            }
        };
    }

    // ── mode INITIATEUR ───────────────────────────────────────────────────────
    /**
     * Lance un chat privé avec {@code target}.
     *
     * Étapes :
     *   1. Envoie "HEY <myName> TO <target>" via la socket TCP du groupe.
     *   2. Attend "300 HEY <target> <ip> <port>" en retour.
     *   3. Ouvre une socket TCP vers le destinataire.
     *   4. Lance la boucle de saisie dans un thread.
     *
     * @param groupTcpSocket socket TCP déjà ouverte vers le serveur de groupe
     */
    public void initiate(Socket groupTcpSocket, String myName, String target) {
        if (groupTcpSocket == null || groupTcpSocket.isClosed()) {
            System.out.println("[PrivateChat] Pas connecté à un groupe — impossible d'envoyer HEY.");
            return;
        }

        try {
            PrintWriter  groupOut = new PrintWriter(groupTcpSocket.getOutputStream(), true);
            BufferedReader groupIn = new BufferedReader(
                    new InputStreamReader(groupTcpSocket.getInputStream()));

            // Envoi du HEY
            String heyMsg = Message.build(Protocol.CMD_HEY, myName, "TO", target);
            groupOut.println(heyMsg);

            // Lecture de la réponse du serveur de groupe
            String response = groupIn.readLine();
            if (response == null) {
                System.out.println("[PrivateChat] Pas de réponse du groupe.");
                return;
            }

            // Attendu : "300 HEY <target> <ip> <port>"
            if (!response.startsWith(String.valueOf(Protocol.CODE_REDIRECT))) {
                System.out.println("[PrivateChat] Réponse inattendue : " + response);
                return;
            }

            Message msg  = Message.parse(response);
            String[] args = msg.getArgs();
            // args = [ "HEY", target, ip, port ]
            if (args.length < 4) {
                System.out.println("[PrivateChat] Réponse mal formée : " + response);
                return;
            }
            String peerIp   = args[2];
            int    peerPort = Integer.parseInt(args[3]);

            // Connexion directe au destinataire
            Socket chatSocket = new Socket(peerIp, peerPort);
            System.out.println("[PrivateChat] Conversation privée ouverte avec " + target);

            // Thread de chat (lecture clavier + envoi)
            chatPool.submit(() -> runChatSession(chatSocket, target, true));

        } catch (IOException e) {
            System.err.println("[PrivateChat] Erreur initiate : " + e.getMessage());
        }
    }

    // ── gestion d'une connexion entrante (mode RÉCEPTEUR) ─────────────────────
    private void handleIncomingChat(Socket socket) {
        String peer = socket.getInetAddress().getHostAddress();
        System.out.println("[PrivateChat] Connexion entrante de " + peer);
        runChatSession(socket, peer, false);
    }

    // ── boucle de chat (commune aux deux modes) ───────────────────────────────
    /**
     * Gère une session de chat bidirectionnelle.
     *
     * Un sous-thread lit les messages entrants ; le thread courant lit le clavier.
     * Tape "bye" pour quitter.
     *
     * @param initiator true si c'est nous qui avons ouvert la connexion
     */
    private void runChatSession(Socket socket, String peerName, boolean initiator) {
        try (
            PrintWriter  out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("[PrivateChat ↔ " + peerName + "] Tapez vos messages. 'bye' pour quitter.");

            // Thread de lecture des messages entrants
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("[" + peerName + "] " + line);
                    }
                } catch (IOException ignored) {}
            });
            reader.setDaemon(true);
            reader.start();

            // Thread courant : lecture clavier + envoi
            while (KBD.hasNextLine()) {
                String input = KBD.nextLine().trim();
                if (input.equalsIgnoreCase("bye")) {
                    out.println("bye");
                    break;
                }
                out.println(input);
            }

        } catch (IOException e) {
            System.err.println("[PrivateChat] Erreur session avec " + peerName + " : " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[PrivateChat] Session terminée avec " + peerName + ".");
        }
    }

    // ── arrêt propre ──────────────────────────────────────────────────────────
    public void stop() {
        chatPool.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}