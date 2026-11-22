package evernight.client.utils.inventory;

public interface AutoMineProvider {
    void collectCandidates(AutoMineCandidates candidates);

    boolean isEnabled();
}