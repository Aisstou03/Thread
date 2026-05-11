package Projet_revision.Server;
import Projet_revision.common.GroupInfo;
 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
 
public class GroupRegistry {
       // Clé = nom du groupe (unique), Valeur = infos du groupe
    private final ConcurrentHashMap<String, GroupInfo> groups = new ConcurrentHashMap<>();
 
    /**
     * Enregistre un nouveau groupe.
     * @return true si succès, false si le nom existe déjà
     */
    public boolean register(GroupInfo group) {
        // putIfAbsent retourne null si la clé n'existait pas → succès
        return groups.putIfAbsent(group.getName(), group) == null;
    }
 
    /**
     * Supprime un groupe (quand le serveur de groupe s'arrête).
     */
    public void unregister(String name) {
        groups.remove(name);
        System.out.println("[Registry] Groupe supprimé : " + name);
    }
 
    /**
     * Liste les groupes filtrés par filière et niveau.
     * Si filiere ou niveau est "*", on ne filtre pas ce critère.
     */
    public List<GroupInfo> list(String filiere, String niveau) {
        List<GroupInfo> result = new ArrayList<>();
        for (GroupInfo g : groups.values()) {
            boolean filiereMatch = filiere.equals("*") || g.getFiliere().equalsIgnoreCase(filiere);
            boolean niveauMatch  = niveau.equals("*")  || g.getNiveau().equalsIgnoreCase(niveau);
            if (filiereMatch && niveauMatch) {
                result.add(g);
            }
        }
        return result;
    }
 
    /**
     * Retourne les infos d'un groupe par son nom.
     * @return GroupInfo ou null si introuvable
     */
    public GroupInfo getInfo(String name) {
        return groups.get(name);
    }
}
