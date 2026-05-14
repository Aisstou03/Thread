package Projet_revision.Client ;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Gère la connexion TCP persistante vers le serveur central (UniversityServer).
 *
 * Méthodes publiques :
 *   connect()             — ouvre la socket
 *   list(filiere, niveau) — envoie LIST, retourne la réponse brute
 *   info(nom)             — envoie INFO, retourne { ip, port } ou null si 404
 *   close()               — ferme la socket
 */
public class ServerConnection {

    private final String host;
    private final int    port;

    private Socket       socket;
    private PrintWriter  out;
    private BufferedReader in;

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── connexion ─────────────────────────────────────────────────────────────
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            out    = new PrintWriter(socket.getOutputStream(), true);   // auto-flush
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return true;
        } catch (IOException e) {
            System.err.println("[ServerConnection] Erreur de connexion : " + e.getMessage());
            return false;
        }
    }

    // ── LIST <filiere> <niveau> ───────────────────────────────────────────────
    /**
     * Envoie "LIST <filiere> <niveau>" au serveur central.
     *
     * Réponse attendue : "200 LIST <n> <g1,g2,...>"
     *                 ou "200 LIST 0 " (aucun groupe)
     *
     * @return la ligne de réponse brute, ou null en cas d'erreur réseau.
     */
    public String list(String filiere, String niveau) {
        String request = Message.build(Protocol.CMD_LIST, filiere, niveau);
        return sendAndReceive(request);
    }

    // ── INFO <nom_groupe> ─────────────────────────────────────────────────────
    /**
     * Envoie "INFO <nom_groupe>" au serveur central.
     *
     * Réponse attendue : "200 INFO <nom> <ip> <port> <filiere> <niveau>"
     *                 ou "404 INFO NOT_FOUND"
     *
     * @return tableau { ip, port, filiere, niveau } si trouvé, null sinon.
     */
    public String[] info(String groupName) {
        String request  = Message.build(Protocol.CMD_INFO, groupName);
        String response = sendAndReceive(request);

        if (response == null) return null;

        Message msg = Message.parse(response);
        if (msg == null) return null;

        // Vérification du code de retour
        if (response.startsWith(String.valueOf(Protocol.CODE_NOT_FOUND))) {
            System.out.println("[INFO] Groupe introuvable : " + groupName);
            return null;
        }

        // Format : 200 INFO <nom> <ip> <port> <filiere> <niveau>
        // args = [ "INFO", nom, ip, port, filiere, niveau ]
        String[] args = msg.getArgs();
        if (args.length < 5) {
            System.err.println("[ServerConnection] Réponse INFO mal formée : " + response);
            return null;
        }
        String ip      = args[2]; // index 0 = "INFO", 1 = nom, 2 = ip, 3 = port, 4 = filiere, 5 = niveau
        String portStr = args[3];
        String filiere = args[4];
        String niveau  = args[5];
        return new String[]{ ip, portStr, filiere, niveau };
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    /**
     * Envoie une ligne et lit la réponse (bloquant).
     */
    private synchronized String sendAndReceive(String message) {
        try {
            out.println(message);           // PrintWriter auto-flush → \n ajouté
            return in.readLine();           // bloque jusqu'à la réponse
        } catch (IOException e) {
            System.err.println("[ServerConnection] Erreur I/O : " + e.getMessage());
            return null;
        }
    }

    // ── fermeture ─────────────────────────────────────────────────────────────
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ServerConnection] Erreur fermeture : " + e.getMessage());
        }
    }
}