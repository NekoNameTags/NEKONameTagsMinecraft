package uk.co.nekosunevr.nekonametags.fabric;

public final class NekoNameTagsFabricApi {
    private NekoNameTagsFabricApi() {
    }

    public static void requestReload() {
        NekoNameTagsFabricClient.requestReload();
    }
}
