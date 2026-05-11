package Projet_revision.Server;
import Projet_revision.common.GroupInfo;
import Projet_revision.common.Protocol;
 
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
 
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GroupRegistry registry;
 
    public ClientHandler(Socket socket, GroupRegistry registry) {
        this.socket = socket;
        this.registry = registry;
    }
 
    @Override
    public void run() {
        String clientAddr = socket.getInetAddress().getHostAddress();
 
        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true) // auto-flush
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
 
                System.out.println("[" + clientAddr + "] >> " + line);
                String response = handleRequest(line);
                System.out.println("[" + clientAddr + "] << " + response);
                out.println(response);
            }
 
        } catch (IOException e) {
            System.err.println("[Handler] Connexion perdue avec " + clientAddr + " : " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Handler] Connexion fermée : " + clientAddr);
        }
    }
 
    /**
     * Analyse la requête et retourne la réponse appropriée.
     */
    private String handleRequest(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length == 0) {
            return Protocol.CODE_BAD_REQUEST + " Requête vide";
        }
 
        String cmd = parts[0].toUpperCase();
 
        switch (cmd) {
 
            // LIST <filiere> <niveau>
            case Protocol.CMD_LIST: {
                if (parts.length < 3) {
                    return Protocol.CODE_BAD_REQUEST + " Usage: LIST <filiere> <niveau>";
                }
                String filiere = parts[1];
                String niveau  = parts[2];
                List<GroupInfo> found = registry.list(filiere, niveau);
 
                if (found.isEmpty()) {
                    return Protocol.CODE_OK + " LIST 0 ";
                }
 
                StringBuilder sb = new StringBuilder();
                sb.append(Protocol.CODE_OK).append(" LIST ").append(found.size()).append(" ");
                for (int i = 0; i < found.size(); i++) {
                    sb.append(found.get(i).getName());
                    if (i < found.size() - 1) sb.append(",");
                }
                return sb.toString();
            }
 
            // INFO <nom_groupe>
            case Protocol.CMD_INFO: {
                if (parts.length < 2) {
                    return Protocol.CODE_BAD_REQUEST + " Usage: INFO <nom_groupe>";
                }
                String name = parts[1];
                GroupInfo g = registry.getInfo(name);
 
                if (g == null) {
                    return Protocol.CODE_NOT_FOUND + " INFO NOT_FOUND";
                }
                // 200 INFO <nom> <ip> <port> <filiere> <niveau>
                return Protocol.CODE_OK + " INFO "
                        + g.getName()    + " "
                        + g.getIp()      + " "
                        + g.getPort()    + " "
                        + g.getFiliere() + " "
                        + g.getNiveau();
            }
 
            // REGISTER <nom> <filiere> <niveau> <matiere> <ip> <port>
            case Protocol.CMD_REGISTER: {
                if (parts.length < 7) {
                    return Protocol.CODE_BAD_REQUEST + " Usage: REGISTER <nom> <filiere> <niveau> <matiere> <ip> <port>";
                }
                String name    = parts[1];
                String filiere = parts[2];
                String niveau  = parts[3];
                String matiere = parts[4];
                String ip      = parts[5];
                int port;
                try {
                    port = Integer.parseInt(parts[6]);
                } catch (NumberFormatException e) {
                    return Protocol.CODE_BAD_REQUEST + " Port invalide";
                }
 
                GroupInfo g = new GroupInfo(name, filiere, niveau, matiere, ip, port);
                boolean ok = registry.register(g);
 
                if (ok) {
                    System.out.println("[Registry] Nouveau groupe enregistré : " + name);
                    return Protocol.CODE_OK + " REGISTER OK";
                } else {
                    return Protocol.CODE_BAD_REQUEST + " REGISTER FAILED nom déjà pris";
                }
            }
 
            default:
                return Protocol.CODE_BAD_REQUEST + " Commande inconnue : " + cmd;
        }
    }
}
