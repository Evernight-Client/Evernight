package evernight.client.events.core;

public enum EventStage {
    Pre, Post;

    public boolean isPre() {
        return this == Pre;
    }

    public boolean isPost() {
        return this == Post;
    }
}