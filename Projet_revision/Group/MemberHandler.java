package Projet_revision.Group;

import Projet_revision.common.Message;
import Projet_revision.common.Protocol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

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
            int memberTcpPort = 0;
            try {
                memberUdpPort = Integer.parseInt(msg.arg(1));
                if (msg.args.length >= 3) {
                    memberTcpPort = Integer.parseInt(msg.arg(2));
                }
            } catch (NumberFormatException e) {
                out.print(Message.build(String.valueOf(Protocol.CODE_BAD_REQUEST), "Invalid port"));
                out.flush();
                return;
            }
            memberIp = socket.getInetAddress().getHostAddress();

            // 3. Enregistrement du membre dans le service de diffusion
            BroadcastService.Member member = new BroadcastService.Member(
                studentName, memberIp, memberUdpPort, memberTcpPort
            );
            broadcastService.addMember(member);
            System.out.println("[MemberHandler] " + studentName + " a rejoint le groupe (UDP "
                               + memberIp + ":" + memberUdpPort + ")");

            // 4. Envoi du message d'accueil
            out.print(Message.build(String.valueOf(Protocol.CODE_OK), "JOIN", "WELCOME", studentName));
            out.flush();

            // 5. Envoi des annonces archivees
            List<String> archive = broadcastService.getArchive();
            System.out.println("[MemberHandler] Envoi de " + archive.size() + " archive(s) a " + studentName);
            for (String archived : archive) {
                System.out.println("[MemberHandler]   -> " + archived);
                out.print(archived + "\n");
            }
            out.print("--- END_ARCHIVE ---\n");
            out.flush();
            System.out.println("[MemberHandler] Archives envoyees a " + studentName);

            // 6. Boucle principale : traite toutes les commandes TCP du membre
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[MemberHandler] " + studentName + " a envoye en TCP : " + line);

                Message tcpMsg = Message.parse(line);
                if (tcpMsg == null) continue;

                // ── MSG <nom> <texte> ────────────────────────────────────────
                // Diffuse le message a tous les membres du groupe via UDP.
                // BroadcastService l'archive aussi car il commence par "MSG".
                if (Protocol.CMD_MSG.equals(tcpMsg.command) && tcpMsg.args.length >= 2) {

                    broadcastService.broadcast(line);
                    out.print(Message.build(String.valueOf(Protocol.CODE_OK), "MSG", "SENT"));
                    out.flush();
                    System.out.println("[MemberHandler] MSG diffuse : " + line);

                // ── PLAN <nom> <date> <heure> <description> ─────────────────
                // Diffuse ET archive automatiquement (commence par "PLAN").
                // Exemple : PLAN Aissatou 2026-05-16 14:00 Revision_TCP_UDP
                } else if (Protocol.CMD_PLAN.equals(tcpMsg.command) && tcpMsg.args.length >= 4) {

                    broadcastService.broadcast(line);
                    out.print(Message.build(String.valueOf(Protocol.CODE_OK), "PLAN", "SENT"));
                    out.flush();
                    System.out.println("[MemberHandler] PLAN diffuse et archive : " + line);

                // ── HEY <source> TO <destination> ───────────────────────────
                } else if (Protocol.CMD_HEY.equals(tcpMsg.command) && tcpMsg.args.length >= 3) {

                    String source      = tcpMsg.args[0];
                    String destination = tcpMsg.args[2];

                    BroadcastService.Member target = null;
                    for (BroadcastService.Member m : broadcastService.getMembers()) {
                        if (m.name.equalsIgnoreCase(destination)) {
                            target = m;
                            break;
                        }
                    }

                    if (target == null) {
                        out.print(Message.build(String.valueOf(Protocol.CODE_NOT_FOUND),
                                "HEY", destination, "NOT_FOUND"));
                        out.flush();
                        System.out.println("[MemberHandler] HEY : " + destination + " introuvable");
                    } else {
                        String response = Message.build(
                            String.valueOf(Protocol.CODE_REDIRECT),
                            "HEY", destination, target.ip, String.valueOf(target.tcpPort)
                        );
                        out.print(response);
                        out.flush();
                        System.out.println("[MemberHandler] HEY : " + source + " -> " + destination
                                        + " (" + target.ip + ":" + target.tcpPort + ")");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[MemberHandler] Erreur : " + e.getMessage());
        } finally {
            if (studentName != null) {
                broadcastService.removeMember(studentName);
                System.out.println("[MemberHandler] " + studentName + " a quitte le groupe");
            }
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}