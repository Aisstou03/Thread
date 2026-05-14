package Projet_revision.Group;

import Projet_revision.common.Protocol;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Diffuse les messages publics a tous les membres du groupe via UDP.
 * Archive egalement les messages importants (PLAN et annonces) pour
 * pouvoir les renvoyer aux nouveaux arrivants.
 *
 * Thread-safe : la liste des membres est une CopyOnWriteArrayList car
 * plusieurs threads y accedent (MemberHandler a l'ajout/retrait, et le
 * thread UDP a la lecture lors du broadcast).
 */
public class BroadcastService {

    /** Represente un membre du groupe avec son adresse UDP de reception. */
    public static class Member {
        public final String name;
        public final String ip;
        public final int udpPort;
        public final int tcpPort;   // NOUVEAU : port TCP prive pour les HEY

        public Member(String name, String ip, int udpPort, int tcpPort) {
            this.name = name;
            this.ip = ip;
            this.udpPort = udpPort;
            this.tcpPort = tcpPort;
        }
    }

    private final List<Member> members = new CopyOnWriteArrayList<>();
    private final List<String> archive = new ArrayList<>(); // PLAN et annonces
    private final DatagramSocket udpSocket;

    public BroadcastService(DatagramSocket udpSocket) {
        this.udpSocket = udpSocket;
    }

    /** Ajoute un membre a la liste des destinataires des broadcasts. */
    public synchronized void addMember(Member m) {
        // Si le même nom de membre se reconnecte, remplacer l'ancienne entrée.
        members.removeIf(existing -> existing.name.equalsIgnoreCase(m.name));
        members.add(m);
        System.out.println("[Broadcast] Membre ajoute : " + m.name + " (" + m.ip + ":" + m.udpPort + ")");
        System.out.println("[Broadcast] Membres actuels : " + members.size());
    }

    /** Retire un membre (deconnexion). */
    public synchronized void removeMember(String name) {
        members.removeIf(m -> m.name.equalsIgnoreCase(name));
        System.out.println("[Broadcast] Membre retire : " + name);
    }

    /**
     * Diffuse un message a tous les membres via UDP.
     * Si le message est un MSG, un PLAN ou une annonce, il est aussi archive.
     */
    public void broadcast(String message) {
        // Archivage des messages importants
        if (message.startsWith(Protocol.CMD_PLAN) || message.startsWith(Protocol.CMD_MSG)
                || message.startsWith("[ANNONCE]")) {
            synchronized (archive) {
                archive.add(message);
            }
        }

        // Preparation des donnees a envoyer
        byte[] data = (message + "\n").getBytes();

        // Envoi a chaque membre
        int sent = 0;
        for (Member m : members) {
            try {
                InetAddress address = InetAddress.getByName(m.ip);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, m.udpPort);
                udpSocket.send(packet);
                sent++;
            } catch (Exception e) {
                System.err.println("[Broadcast] Echec envoi a " + m.name + " : " + e.getMessage());
            }
        }
        System.out.println("[Broadcast] Message diffuse a " + sent + " membre(s) : " + message);
    }

    /** Retourne une copie de l'archive des messages importants. */
    public synchronized List<String> getArchive() {
        return new ArrayList<>(archive);
    }

    public int getMemberCount() {
        return members.size();
    }

    /** Retourne une copie de la liste des membres. */
    public List<Member> getMembers() {
        return new ArrayList<>(members);
    }
}
