package Projet_revision.Client;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.*;
import java.net.*;
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

    public interface SessionListener {
        void onSessionStarted(ChatSession session, String peerName, boolean initiator);
        void onSessionEnded(String peerName);
    }

    public class ChatSession {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private final String peerName;
        private final Runnable onClose;
        private volatile boolean open = true;

        private ChatSession(Socket socket, String peerName, PrintWriter out, BufferedReader in,
                            boolean initiator, Runnable onClose) {
            this.socket = socket;
            this.out = out;
            this.in = in;
            this.peerName = peerName;
            this.onClose = onClose;
        }

        public String getPeerName() {
            return peerName;
        }

        public boolean isOpen() {
            return open && !socket.isClosed();
        }

        public void send(String message) {
            if (!isOpen()) {
                System.out.println("[PrivateChat] Session fermée avec " + peerName + ".");
                return;
            }
            out.println(message);
            out.flush();
        }

        public void close() {
            if (!open) return;
            open = false;
            try {
                out.flush();
            } catch (Exception ignored) {}
            try {
                socket.close();
            } catch (IOException ignored) {}
            if (onClose != null) {
                onClose.run();
            }
        }

        private void runIncomingReader() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if ("bye".equalsIgnoreCase(line.trim())) {
                        System.out.println("[PrivateChat] Session terminée par " + peerName + ".");
                        close();
                        return;
                    }
                    System.out.println("[" + peerName + "] " + line);
                }
            } catch (IOException ignored) {
            } finally {
                if (isOpen()) {
                    System.out.println("[PrivateChat] Session fermée avec " + peerName + ".");
                }
                close();
            }
        }
    }

    private final ServerSocket      serverSocket;   // écoute les HEY entrants
    private final ExecutorService   chatPool;
    private SessionListener sessionListener;

    public void setSessionListener(SessionListener listener) {
        this.sessionListener = listener;
    }

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
     *   4. Lance la session de chat.
     *
     * @param groupOut writer TCP vers le serveur de groupe
     * @param groupIn  reader TCP vers le serveur de groupe
     */
    public ChatSession initiate(PrintWriter groupOut, BufferedReader groupIn, String myName, String target) {
        if (groupOut == null || groupIn == null) {
            System.out.println("[PrivateChat] Pas connecté à un groupe — impossible d'envoyer HEY.");
            return null;
        }

        try {
            String heyMsg = Message.build(Protocol.CMD_HEY, myName, "TO", target);
            groupOut.println(heyMsg);

            String response = groupIn.readLine();
            if (response == null) {
                System.out.println("[PrivateChat] Pas de réponse du groupe.");
                return null;
            }

            if (!response.startsWith(String.valueOf(Protocol.CODE_REDIRECT))) {
                System.out.println("[PrivateChat] Réponse inattendue : " + response);
                return null;
            }

            Message msg  = Message.parse(response);
            String[] args = msg.getArgs();
            if (args.length < 4) {
                System.out.println("[PrivateChat] Réponse mal formée : " + response);
                return null;
            }
            String peerIp   = args[2];
            int    peerPort = Integer.parseInt(args[3]);

            System.out.println("[PrivateChat] Connexion vers " + target);
            if (peerIp == null || peerIp.isBlank() || peerPort <= 0) {
                System.out.println("[PrivateChat] Adresse de pair invalide : ip=" + peerIp + " port=" + peerPort);
                return null;
            }

            Socket chatSocket = new Socket(peerIp, peerPort);
            PrintWriter tempOut = new PrintWriter(chatSocket.getOutputStream(), true);
            tempOut.println(myName); // Envoi du nom en premier
            BufferedReader tempIn = new BufferedReader(new InputStreamReader(chatSocket.getInputStream()));
            ChatSession session = createChatSession(chatSocket, target, tempOut, tempIn, true);
            if (sessionListener != null) {
                sessionListener.onSessionStarted(session, target, true);
            }
            return session;

        } catch (IOException e) {
            System.err.println("[PrivateChat] Erreur initiate : " + e.getMessage());
            return null;
        }
    }

    // ── gestion d'une connexion entrante (mode RÉCEPTEUR) ─────────────────────
    private void handleIncomingChat(Socket socket) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String peerName = in.readLine(); // Lecture du nom envoyé par l'initiateur
            if (peerName == null || peerName.trim().isEmpty()) {
                System.err.println("[PrivateChat] Nom de pair non reçu, fermeture.");
                socket.close();
                return;
            }
            ChatSession session = createChatSession(socket, peerName.trim(), out, in, false);
            if (sessionListener != null) {
                sessionListener.onSessionStarted(session, peerName.trim(), false);
            }
        } catch (IOException e) {
            System.err.println("[PrivateChat] Erreur ouverture session entrante : " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private ChatSession createChatSession(Socket socket, String peerName, PrintWriter out, BufferedReader in, boolean initiator) {
        ChatSession session = new ChatSession(socket, peerName, out, in, initiator, () -> {
            if (sessionListener != null) {
                sessionListener.onSessionEnded(peerName);
            }
        });
        System.out.println("[PrivateChat] Conversation privée ouverte avec " + peerName);
        System.out.println("[PrivateChat ↔ " + peerName + "] Tapez vos messages. 'bye' pour quitter.");
        chatPool.submit(session::runIncomingReader);
        return session;
    }

    // ── arrêt propre ──────────────────────────────────────────────────────────
    public void stop() {
        chatPool.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}