package Projet_revision.Server;
 
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
 
public class UniversityServer {

     private static final int PORT = 5000;
 
    public static void main(String[] args) {
        GroupRegistry registry = new GroupRegistry();
 
        System.out.println("=== Serveur Université démarré sur le port " + PORT + " ===");
 
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Attente d'une nouvelle connexion (bloquant)
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Serveur] Nouvelle connexion : "
                        + clientSocket.getInetAddress().getHostAddress());
 
                // Lancer un thread pour gérer ce client
                Thread t = new Thread(new ClientHandler(clientSocket, registry));
                t.setDaemon(true); // Le thread s'arrête avec le serveur
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[Serveur] Erreur fatale : " + e.getMessage());
        }
    }
}
