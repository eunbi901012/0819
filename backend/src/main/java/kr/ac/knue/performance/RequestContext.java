package kr.ac.knue.performance;

public final class RequestContext {
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setActor(String userId) {
        ACTOR.set(userId);
    }

    public static String actor() {
        String actor = ACTOR.get();
        return actor == null ? "U-ADMIN" : actor;
    }

    public static void clear() {
        ACTOR.remove();
    }
}
