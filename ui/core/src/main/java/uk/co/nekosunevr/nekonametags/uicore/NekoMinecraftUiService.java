package uk.co.nekosunevr.nekonametags.uicore;

import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebBigTag;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebClient;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebState;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebTag;

import java.util.ArrayList;
import java.util.List;

public final class NekoMinecraftUiService {
    private NekoMinecraftUiService() {
    }

    public static void saveLocalSettings(NekoClientSettings settings, String baseUrl, String apiKey) {
        settings.setWebApiBaseUrl(baseUrl);
        settings.setWebApiKey(apiKey);
        settings.saveDefault();
    }

    public static NekoMinecraftWebClient newClient(String baseUrl, String apiKey) {
        return new NekoMinecraftWebClient(baseUrl.trim(), apiKey.trim());
    }

    public static NekoMinecraftUiSnapshot loadSnapshot(String baseUrl, String apiKey) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        NekoMinecraftWebSettings settings = client.fetchSettings();
        NekoMinecraftWebState state = client.fetchState();
        return new NekoMinecraftUiSnapshot(settings, state);
    }

    public static NekoMinecraftUiSnapshot updateSync(String baseUrl, String apiKey, boolean enabled) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        NekoMinecraftWebSettings settings = client.updateSettings(enabled);
        NekoMinecraftWebState state = client.fetchState();
        return new NekoMinecraftUiSnapshot(settings, state);
    }

    public static NekoMinecraftUiSnapshot createTag(
        String baseUrl,
        String apiKey,
        String cleanText,
        String bigText,
        String size,
        boolean hasAnimation,
        String animationType
    ) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        client.createTag(cleanText, bigText, size, hasAnimation, animationType);
        return new NekoMinecraftUiSnapshot(client.fetchSettings(), client.fetchState());
    }

    public static NekoMinecraftUiSnapshot updateTag(
        String baseUrl,
        String apiKey,
        int id,
        String cleanText,
        String size,
        boolean hasAnimation,
        String animationType,
        boolean active
    ) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        client.updateTag(id, cleanText, size, hasAnimation, animationType, active);
        return new NekoMinecraftUiSnapshot(client.fetchSettings(), client.fetchState());
    }

    public static NekoMinecraftUiSnapshot updateBigTag(
        String baseUrl,
        String apiKey,
        int id,
        String text,
        boolean active
    ) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        client.updateBigTag(id, text, active);
        return new NekoMinecraftUiSnapshot(client.fetchSettings(), client.fetchState());
    }

    public static NekoMinecraftUiSnapshot deleteTag(String baseUrl, String apiKey, int id) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        client.deleteTag(id);
        return new NekoMinecraftUiSnapshot(client.fetchSettings(), client.fetchState());
    }

    public static NekoMinecraftUiSnapshot deleteBigTag(String baseUrl, String apiKey, int id) throws Exception {
        NekoMinecraftWebClient client = newClient(baseUrl, apiKey);
        client.deleteBigTag(id);
        return new NekoMinecraftUiSnapshot(client.fetchSettings(), client.fetchState());
    }

    public static List<NekoMinecraftUiRow> buildRows(NekoMinecraftWebState state) {
        List<NekoMinecraftUiRow> rows = new ArrayList<NekoMinecraftUiRow>();
        if (state == null) {
            return rows;
        }
        for (NekoMinecraftWebTag tag : state.getTags()) {
            rows.add(NekoMinecraftUiRow.normal(tag));
        }
        for (NekoMinecraftWebBigTag bigTag : state.getBigTags()) {
            rows.add(NekoMinecraftUiRow.big(bigTag));
        }
        return rows;
    }
}
