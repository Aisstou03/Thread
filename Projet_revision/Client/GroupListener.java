package Projet_revision.Client;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Runnable qui tourne dans un thread dédié.
 *
 * Responsabilités :
 *   1. Ouvre un DatagramSocket sur un port libre → reçoit les MSG/PLAN UDP du groupe.
 *   2. Garde une socket TCP vers le serveur de groupe (JOIN, envoi de commandes de contrôle).
 *   3. Expose sendMessage() pour que StudentClient puisse envoyer SAY/PLAN via TCP
 *      (le serveur de groupe fera le broadcast UDP).
 */
public class GroupListener implements Runnable {

    private static final int BUFFER_SIZE = 4096;

    

    // ── UDP (réception des broadcasts du groupe) ──────────────────────────────
    private final DatagramSocket udpSocket;

    // ── TCP (connexion au serveur de groupe) ──────────────────────────────────
    private Socket         groupSocket;
    private PrintWriter    groupOut;
    private BufferedReader groupIn;
    private String groupIp;
    private int    groupUdpPort;
    private String groupName;  // nom du groupe actuel

    private volatile boolean running = true;

    // ── constructeur ──────────────────────────────────────────────────────────
    public GroupListener() {
        DatagramSocket tmp = null;
        try {
            tmp = new DatagramSocket(); // port libre attribué par l'OS
        } catch (SocketException e) {
            System.err.println("[GroupListener] Impossible d'ouvrir le socket UDP : " + e.getMessage());
        }
        udpSocket = tmp;
    }

    // ── port UDP local (communiqué au serveur de groupe lors du JOIN) ─────────
    public int getLocalPort() {
        return udpSocket != null ? udpSocket.getLocalPort() : -1;
    }

    // ── rejoindre un groupe (TCP) ─────────────────────────────────────────────
    /**
     * Ouvre une connexion TCP vers le serveur de groupe et envoie JOIN.
     *
     * Protocole : "JOIN <nom_etudiant> <port_udp>"
     * Le serveur répond : "200 JOIN OK" puis renvoie les annonces archivées.
     *
     * @return true si le JOIN a réussi.
     */
    public boolean joinGroup(String groupName, String groupIp, int groupPort, String studentName, int udpPort, int privateTcpPort) {
    try {
        groupSocket = new Socket(groupIp, groupPort);
        groupOut    = new PrintWriter(groupSocket.getOutputStream(), true);
        groupIn     = new BufferedReader(new InputStreamReader(groupSocket.getInputStream()));

            //mémoriser l'adresse UDP du groupe (convention : port UDP = port TCP + 1)
            this.groupName   = groupName;
            this.groupIp      = groupIp;
            this.groupUdpPort = groupPort + 1;

            // Envoi du JOIN
            String joinMsg = Message.build(Protocol.CMD_JOIN, studentName, String.valueOf(udpPort), String.valueOf(privateTcpPort));
            groupOut.println(joinMsg);

            // Lecture du message d'accueil + annonces archivées
            // Lecture du message d'accueil + annonces archivées
            // On lit toutes les lignes jusqu'au marqueur de fin "--- END_ARCHIVE ---"
            String line;
            while ((line = groupIn.readLine()) != null) {
                // Marqueur de fin envoyé par le serveur → on sort de la boucle
                if (line.startsWith("--- END_ARCHIVE ---")) {
                    break;
                }
                System.out.println("[Groupe - " + groupName + "] " + line);
            }
            return true;

        } catch (IOException e) {
            System.err.println("[GroupListener] Erreur joinGroup : " + e.getMessage());
            return false;
        }
    }

    // ── envoi d'un message au groupe (TCP → le serveur broadcast en UDP) ──────
    /**
     * Envoie "MSG <nom> <texte>" ou "PLAN <nom> <texte>" au serveur de groupe via TCP.
     * C'est le serveur de groupe qui se charge du broadcast UDP vers tous les membres.
     */
    public void sendMessage(String cmd, String senderName, String text) {
    if (udpSocket == null || groupIp == null) {
        System.out.println("[GroupListener] Pas connecté à un groupe.");
        return;
    }
    try {
        String msg = Message.build(cmd, senderName, text);
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
            data, data.length,
            InetAddress.getByName(groupIp),
            groupUdpPort
        );
        udpSocket.send(packet);
    } catch (IOException e) {
        System.err.println("[GroupListener] Erreur envoi UDP : " + e.getMessage());
    }
    }

    // ── accès à la socket TCP du groupe (utilisé par PrivateChatHandler) ──────
    public Socket getGroupTcpSocket() {
        return groupSocket;
    }

    public PrintWriter getGroupOut() {
        return groupOut;
    }

    public BufferedReader getGroupIn() {
        return groupIn;
    }

    // ── boucle principale : écoute UDP ────────────────────────────────────────
    @Override
    public void run() {
        if (udpSocket == null) return;

        byte[] buffer = new byte[BUFFER_SIZE];
        System.out.println("[GroupListener] En écoute UDP sur le port " + udpSocket.getLocalPort());

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);          // bloquant

                String received = new String(
                    packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8
                ).trim();

                displayGroupMessage(received);

            } catch (SocketException e) {
                if (running) {
                    System.err.println("[GroupListener] Socket fermée de façon inattendue.");
                }
                // Si running == false, c'est un arrêt voulu
            } catch (IOException e) {
                System.err.println("[GroupListener] Erreur réception UDP : " + e.getMessage());
            }
        }
    }

    // ── affichage formaté des messages reçus ──────────────────────────────────
    private void displayGroupMessage(String raw) {
        Message msg = Message.parse(raw);
        if (msg == null) {
            System.out.println("[Groupe - " + groupName + "] " + raw);
            return;
        }

        String[] args = msg.args;
        switch (msg.command) {
            case Protocol.CMD_MSG:
                // MSG <expediteur> <texte…>
                if (args.length >= 2) {
                    System.out.println("[Groupe - " + groupName + "][" + args[0] + "] " + joinFrom(args, 1));
                }
                break;
            case Protocol.CMD_PLAN:
                // PLAN <expediteur> <texte…>
                if (args.length >= 2) {
                    System.out.println("[📅 PLAN - " + groupName + "][" + args[0] + "] " + joinFrom(args, 1));
                }
                break;
            default:
                System.out.println("[Groupe - " + groupName + "] " + raw);
        }
    }

    /** Concatène les éléments d'un tableau à partir de l'index start. */
    private String joinFrom(String[] arr, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    // ── arrêt propre ──────────────────────────────────────────────────────────
    public void stop() {
        running = false;
        if (udpSocket != null) udpSocket.close();
        try {
            if (groupSocket != null) groupSocket.close();
        } catch (IOException ignored) {}
    }
}