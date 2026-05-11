package Projet_revision.common;

public class Message {
    public final String   command;
    public final String[] args;
 
    private Message(String command, String[] args) {
        this.command = command;
        this.args    = args;
    }
 
    public String getCommand() {
        return command;
    }
 
    public String[] getArgs() {
        return args;
    }
 
    /**
     * Parse une ligne texte reçue sur le réseau.
     * Exemple : "LIST INFO M1" → command="LIST", args=["INFO","M1"]
     */
    public static Message parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new Message("", new String[0]);
        }
 
        String[] parts = line.trim().split("\\s+");
        String command = parts[0].toUpperCase();
 
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
 
        return new Message(command, args);
    }
 
    /**
     * Construit une ligne texte à envoyer sur le réseau.
     * Exemple : build("LIST", "INFO", "M1") → "LIST INFO M1\n"
     */
    public static String build(String cmd, Object... args) {
        StringBuilder sb = new StringBuilder(cmd);
        for (Object arg : args) {
            sb.append(" ").append(arg);
        }
        sb.append("\n");
        return sb.toString();
    }
 
    /**
     * Retourne l'argument à l'index donné, ou null si inexistant.
     * Evite les IndexOutOfBoundsException dans le code appelant.
     */
    public String arg(int index) {
        if (index < 0 || index >= args.length) return null;
        return args[index];
    }
 
    @Override
    public String toString() {
        return build(command, (Object[]) args).trim();
    }
}
