package Projet_revision.Group;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

/**
 * Gere un etudiant qui vient de se connecter en TCP au serveur de groupe.
 *
 * Quand un client se connecte, il envoie en premier :
 *   JOIN <nom_etudiant> <port_udp>
 *
 * Le port UDP est important : c'est sur ce port que le client ecoute les
 * diffusions du groupe. On le memorise pour pouvoir lui envoyer les MSG/PLAN.
 *
 * Apres le JOIN, le handler envoie au nouveau membre :
 *   1. Un message d'accueil
 *   2. Les annonces archivees (PLAN passes, annonces importantes)
 */
public class MemberHandler implements Runnable {

    private final Socket socket;
    private final BroadcastService broadcastService;

    private String studentName;
    private String memberIp;
    private int memberUdpPort;

    public MemberHandler(Socket socket, BroadcastService broadcastService) {
        this.socket = socket;
        this.broadcastService = broadcastService;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // 1. Lecture de la premiere ligne : doit etre un JOIN
            String firstLine = in.readLine();
            if (firstLine == null) {
                System.err.println("[MemberHandler] Connexion fermee avant JOIN");
                return;
            }

            Message msg = Message.parse(firstLine);
            if (!Protocol.CMD_JOIN.equals(msg.command) || msg.args.length < 2) {
                out.print(Message.build(String.valueOf(Protocol.CODE_BAD_REQUEST),
                          "Expected: JOIN <nom> <port_udp>"));
                out.flush();
                return;
            }

            // 2. Extraction des infos du membre
            studentName = msg.arg(0);
            try {
                memberUdpPort = Integer.parseInt(msg.arg(1));
            } catch (NumberFormatException e) {
                out.print(Message.build(String.valueOf(Protocol.CODE_BAD_REQUEST),
                          "Invalid UDP port"));
                out.flush();
                return;
            }
            memberIp = socket.getInetAddress().getHostAddress();

            // 3. Enregistrement du membre dans le service de diffusion
            BroadcastService.Member member = new BroadcastService.Member(studentName, memberIp, memberUdpPort);
            broadcastService.addMember(member);

            System.out.println("[MemberHandler] " + studentName + " a rejoint le groupe (UDP "
                               + memberIp + ":" + memberUdpPort + ")");

            // 4. Envoi du message d'accueil
            out.print(Message.build(String.valueOf(Protocol.CODE_OK), "JOIN", "WELCOME", studentName));
            out.flush();

            // 5. Envoi des annonces archivees
            List<String> archive = broadcastService.getArchive();
            for (String archived : archive) {
                out.print(archived + "\n");
            }
            out.flush();

            // 6. On garde la connexion TCP ouverte pour detecter le depart du membre
            // (quand readLine() renvoie null, c'est que le client a ferme)
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[MemberHandler] " + studentName + " a envoye en TCP : " + line);
            }

        } catch (Exception e) {
            System.err.println("[MemberHandler] Erreur : " + e.getMessage());
        } finally {
            // Le membre s'est deconnecte : on le retire de la liste
            if (studentName != null) {
                broadcastService.removeMember(studentName);
                System.out.println("[MemberHandler] " + studentName + " a quitte le groupe");
            }
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
