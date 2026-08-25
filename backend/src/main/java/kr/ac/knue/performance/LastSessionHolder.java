package kr.ac.knue.performance;

public final class LastSessionHolder {
    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();

    private LastSessionHolder() {
    }

    public static void set(String sessionId) {
        SESSION.set(sessionId);
    }

    public static String take() {
        String sessionId = SESSION.get();
        SESSION.remove();
        return sessionId == null ? "" : sessionId;
    }
}
