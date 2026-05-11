package Projet_revision.common;

public class Protocol {
     // ── Codes de réponse ────────────────────────────────────────────
    public static final int CODE_OK           = 200;
    public static final int CODE_REDIRECT     = 300;
    public static final int CODE_BAD_REQUEST  = 400;
    public static final int CODE_NOT_FOUND    = 404;
    public static final int CODE_SERVER_ERROR = 500;
 
    // ── Commandes ───────────────────────────────────────────────────
    public static final String CMD_LIST     = "LIST";
    public static final String CMD_INFO     = "INFO";
    public static final String CMD_JOIN     = "JOIN";
    public static final String CMD_MSG      = "MSG";
    public static final String CMD_HEY      = "HEY";
    public static final String CMD_PLAN     = "PLAN";
    public static final String CMD_REGISTER = "REGISTER";
 
    // Empêcher l'instanciation
    private Protocol() {}
}
