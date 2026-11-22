package evernight.client.utils.combat;

public interface EndCrystalState {
    long getSpawnTime();

    boolean isClientAttacked();

    void setClientAttacked(boolean attacked);
}