package Projet_revision.Group;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Serveur de groupe : un salon de chat thematique (filiere + niveau + matiere).
 *
 * Au demarrage :
 *   1. S'enregistre aupres du serveur central via REGISTER (TCP)
 *   2. Ouvre un ServerSocket TCP pour accepter les JOIN des etudiants
 *   3. Ouvre un DatagramSocket UDP pour recevoir les MSG et PLAN
 *
 * Lancement :
 *   java Projet_revision.Group.GroupServer <nom_groupe> <filiere> <niveau> <matiere>
 * Exemple :
 *   java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux
 */
public class GroupServer {

    // Ports par defaut (a ajuster si conflit)
    private static final int SERVER_CENTRAL_PORT = 5000;
    private static final int GROUP_TCP_PORT = 6000;
    private static final int GROUP_UDP_PORT = 6001;

    // Identite du groupe
    private final String name;
    private final String filiere;
    private final String niveau;
    private final String matiere;

    // Ports d'ecoute
    private final int tcpPort;
    private final int udpPort;

    // Adresse du serveur central
    private final String centralHost;
    private final int centralPort;

    // Service de diffusion partage entre TCP et UDP
    private BroadcastService broadcastService;

    public GroupServer(String name, String filiere, String niveau, String matiere,
                       int tcpPort, int udpPort,
                       String centralHost, int centralPort) {
        this.name = name;
        this.filiere = filiere;
        this.niveau = niveau;
        this.matiere = matiere;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.centralHost = centralHost;
        this.centralPort = centralPort;
    }

    /**
     * Demarre le serveur de groupe : enregistrement central + ecoute TCP + ecoute UDP.
     */
    public void start() throws Exception {
        // 1. S'enregistrer aupres du serveur central (TCP)
        registerWithCentralServer();

        // 2. Creer la socket UDP (utilisee pour la diffusion ET la reception)
        DatagramSocket udpSocket;
        try {
            udpSocket = new DatagramSocket(udpPort);
        } catch (java.net.BindException e) {
            throw new Exception("Impossible d'ouvrir le port UDP " + udpPort + ". Il est déjà utilisé.", e);
        }
        broadcastService = new BroadcastService(udpSocket);

        // 3. Demarrer l'ecoute TCP dans un thread (pour les JOIN)
        Thread tcpThread = new Thread(this::runTcpListener, "TCP-Listener");
        tcpThread.start();

        // 4. Demarrer l'ecoute UDP dans un thread (pour les MSG / PLAN)
        Thread udpThread = new Thread(() -> runUdpListener(udpSocket), "UDP-Listener");
        udpThread.start();

        System.out.println("[GroupServer] Groupe " + name + " pret (TCP:" + tcpPort + ", UDP:" + udpPort + ")");
    }

    /**
     * Ouvre une connexion TCP vers le serveur central et envoie un REGISTER.
     * Format : REGISTER <nom> <filiere> <niveau> <matiere> <ip> <port>
     */
    private void registerWithCentralServer() throws Exception {
        System.out.println("[GroupServer] Enregistrement aupres du serveur central " + centralHost + ":" + centralPort);

        try (Socket socket = new Socket(centralHost, centralPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // On recupere l'IP locale pour la fournir au serveur central
            String localIp = InetAddress.getLocalHost().getHostAddress();

            // Construction et envoi de la commande REGISTER
            String registerMsg = Message.build(
                Protocol.CMD_REGISTER,
                name, filiere, niveau, matiere, localIp, tcpPort
            );
            out.print(registerMsg);
            out.flush();

            // Lecture de la reponse
            String response = in.readLine();
            System.out.println("[GroupServer] Reponse du serveur central : " + response);

            if (response == null || !response.startsWith(String.valueOf(Protocol.CODE_OK))) {
                throw new Exception("Echec de l'enregistrement : " + response);
            }
        }
    }

    /**
     * Boucle d'ecoute TCP : accepte les JOIN et lance un MemberHandler par membre.
     */
    private void runTcpListener() {
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            System.out.println("[GroupServer] Ecoute TCP sur le port " + tcpPort);

            while (true) {
                Socket memberSocket = serverSocket.accept();
                System.out.println("[GroupServer] Nouvelle connexion TCP de " + memberSocket.getInetAddress());

                // Un thread par membre
                new Thread(new MemberHandler(memberSocket, broadcastService), "Member-Handler").start();
            }
        } catch (java.net.BindException e) {
            System.err.println("[GroupServer] Impossible d'ouvrir le port TCP " + tcpPort + " : il est déjà utilisé.");
        } catch (Exception e) {
            System.err.println("[GroupServer] Erreur ecoute TCP : " + e.getMessage());
        }
    }

    /**
     * Boucle d'ecoute UDP : recoit les MSG et PLAN, les diffuse a tous les membres.
     */
    private void runUdpListener(DatagramSocket udpSocket) {
        byte[] buffer = new byte[2048];
        System.out.println("[GroupServer] Ecoute UDP sur le port " + udpPort);

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet); // bloque jusqu'a reception

                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("[GroupServer] UDP recu : " + received);

                // Diffusion a tous les membres du groupe
                broadcastService.broadcast(received);

            } catch (Exception e) {
                System.err.println("[GroupServer] Erreur ecoute UDP : " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage : java Projet_revision.Group.GroupServer <nom> <filiere> <niveau> <matiere> [tcpPort] [udpPort]");
            System.err.println("Exemple : java Projet_revision.Group.GroupServer M1-INFO-Reseaux Informatique M1 Reseaux 6000 6001");
            System.exit(1);
        }
        String name = args[0];
        String filiere = args[1];
        String niveau = args[2];
        String matiere = args[3];
        int tcpPort = (args.length > 4) ? Integer.parseInt(args[4]) : GROUP_TCP_PORT;
        int udpPort = (args.length > 5) ? Integer.parseInt(args[5]) : GROUP_UDP_PORT;
        GroupServer server = new GroupServer(
            name, filiere, niveau, matiere,
            tcpPort, udpPort,
            "localhost", SERVER_CENTRAL_PORT
        );
        server.start();
    }
}
