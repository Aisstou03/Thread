package Projet_revision.common;

public class GroupInfo {
    private String name;
    private String filiere;
    private String niveau;
    private String matiere;
    private String ip;
    private int    port;
 
    public GroupInfo(String name, String filiere, String niveau,
                     String matiere, String ip, int port) {
        this.name    = name;
        this.filiere = filiere;
        this.niveau  = niveau;
        this.matiere = matiere;
        this.ip      = ip;
        this.port    = port;
    }
 
    // ── Getters ─────────────────────────────────────────────────────
    public String getName()    { return name;    }
    public String getFiliere() { return filiere; }
    public String getNiveau()  { return niveau;  }
    public String getMatiere() { return matiere; }
    public String getIp()      { return ip;      }
    public int    getPort()    { return port;     }
 
    // ── Setters ─────────────────────────────────────────────────────
    public void setName(String name)       { this.name    = name;    }
    public void setFiliere(String filiere) { this.filiere = filiere; }
    public void setNiveau(String niveau)   { this.niveau  = niveau;  }
    public void setMatiere(String matiere) { this.matiere = matiere; }
    public void setIp(String ip)           { this.ip      = ip;      }
    public void setPort(int port)          { this.port    = port;    }
 
    @Override
    public String toString() {
        return name + " [" + filiere + "/" + niveau + "] " + ip + ":" + port;
    }
}
