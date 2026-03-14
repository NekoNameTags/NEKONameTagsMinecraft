package uk.co.nekosunevr.nekonametags.fabricui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebBigTag;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebClient;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebState;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebTag;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class NekoNameTagsFabricUiScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("NekoNameTags-UI");

    private final Screen parent;
    private final NekoClientSettings settings;

    private TextFieldWidget baseUrlBox;
    private TextFieldWidget apiKeyBox;
    private TextFieldWidget tagNameBox;
    private TextFieldWidget bigTextBox;
    private TextFieldWidget sizeBox;

    private ButtonWidget syncToggleButton;
    private ButtonWidget animationButton;
    private ButtonWidget activeToggleButton;
    private ButtonWidget updateButton;
    private ButtonWidget deleteButton;

    private NekoMinecraftWebState state;
    private NekoMinecraftWebSettings webSettings;
    private String statusMessage = "Load your Minecraft data with Refresh.";
    private boolean busy;
    private SelectionKind selectionKind = SelectionKind.NONE;
    private int selectedId = -1;
    private boolean selectedActive = true;
    private boolean selectedHasAnimation;
    private String selectedAnimationType = "";

    NekoNameTagsFabricUiScreen(Screen parent) {
        super(Text.literal("NekoNameTags Minecraft Manager"));
        this.parent = parent;
        this.settings = NekoClientSettings.loadDefault();
    }

    @Override
    protected void init() {
        int left = 20;
        int top = 24;
        int fieldWidth = Math.min(320, this.width - 40);
        int buttonWidth = 150;

        baseUrlBox = addDrawableChild(new TextFieldWidget(this.textRenderer, left, top + 12, fieldWidth, 20, Text.literal("Web API Base URL")));
        baseUrlBox.setText(settings.getWebApiBaseUrl());
        apiKeyBox = addDrawableChild(new TextFieldWidget(this.textRenderer, left, top + 44, fieldWidth, 20, Text.literal("API Key")));
        apiKeyBox.setText(settings.getWebApiKey());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save Local API Settings"), button -> {
            saveLocalSettings();
            statusMessage = "Saved local API settings.";
        }).dimensions(left + fieldWidth + 10, top + 12, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Minecraft Data"), button -> {
            saveLocalSettings();
            loadMinecraftState();
        }).dimensions(left + fieldWidth + 10, top + 44, buttonWidth, 20).build());

        syncToggleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Minecraft Sync: OFF"), button -> toggleMinecraftSync())
            .dimensions(left, top + 76, buttonWidth, 20).build());

        tagNameBox = addDrawableChild(new TextFieldWidget(this.textRenderer, left, top + 118, fieldWidth, 20, Text.literal("Tag Text")));
        bigTextBox = addDrawableChild(new TextFieldWidget(this.textRenderer, left, top + 150, fieldWidth, 20, Text.literal("Big Tag Text")));
        sizeBox = addDrawableChild(new TextFieldWidget(this.textRenderer, left, top + 182, 80, 20, Text.literal("Size")));
        sizeBox.setText("1");

        animationButton = addDrawableChild(ButtonWidget.builder(Text.literal("Animation: None"), button -> cycleAnimation())
            .dimensions(left + 90, top + 182, 130, 20).build());
        activeToggleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Selected Active: ON"), button -> {
            selectedActive = !selectedActive;
            refreshButtons();
        }).dimensions(left + 230, top + 182, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Create Minecraft Tag"), button -> createTag())
            .dimensions(left, top + 214, buttonWidth, 20).build());
        updateButton = addDrawableChild(ButtonWidget.builder(Text.literal("Update Selected"), button -> updateSelected())
            .dimensions(left + 160, top + 214, buttonWidth, 20).build());
        deleteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete Selected"), button -> deleteSelected())
            .dimensions(left + 320, top + 214, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
            .dimensions(this.width - 90, this.height - 28, 70, 20).build());

        refreshButtons();
        if (settings.hasWebApiKey()) {
            loadMinecraftState();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, this.title, 20, 8, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal("Web API Base URL"), 20, 26, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("API Key"), 20, 58, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("Tag Text"), 20, 132, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("Big Tag Text"), 20, 164, 0xA0A0A0, false);
        context.drawText(this.textRenderer, Text.literal("Status: " + statusMessage), 20, this.height - 18, busy ? 0xFFFF55 : 0x55FF55, false);

        int listLeft = 20;
        int listTop = 248;
        int listWidth = this.width - 40;
        int rowHeight = 18;
        context.fill(listLeft, listTop - 4, listLeft + listWidth, this.height - 38, 0x66000000);
        context.drawText(this.textRenderer, Text.literal("Minecraft Tags"), listLeft + 4, listTop - 14, 0xFFFFFF, false);

        List<RowEntry> rows = buildRows();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = listTop + (i * rowHeight);
            if (rowY + rowHeight > this.height - 40) {
                break;
            }
            RowEntry row = rows.get(i);
            boolean selected = row.kind == selectionKind && row.id == selectedId;
            int background = selected ? 0xAA224466 : 0x55222222;
            context.fill(listLeft + 2, rowY, listLeft + listWidth - 2, rowY + rowHeight - 2, background);
            context.drawText(this.textRenderer, Text.literal(row.label), listLeft + 6, rowY + 5, row.active ? 0xFFFFFF : 0xFF7777, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int listLeft = 20;
        int listTop = 248;
        int listWidth = this.width - 40;
        int rowHeight = 18;
        if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop) {
            int index = (int) ((mouseY - listTop) / rowHeight);
            List<RowEntry> rows = buildRows();
            if (index >= 0 && index < rows.size()) {
                applySelection(rows.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void saveLocalSettings() {
        settings.setWebApiBaseUrl(baseUrlBox.getText());
        settings.setWebApiKey(apiKeyBox.getText());
        settings.saveDefault();
    }

    private void loadMinecraftState() {
        if (busy) {
            return;
        }
        if (apiKeyBox.getText().trim().isEmpty()) {
            statusMessage = "Enter your WEB API key first.";
            return;
        }
        busy = true;
        statusMessage = "Loading Minecraft settings and tags...";
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftWebClient client = newClient();
                NekoMinecraftWebSettings loadedSettings = client.fetchSettings();
                NekoMinecraftWebState loadedState = client.fetchState();
                MinecraftClient.getInstance().execute(() -> {
                    webSettings = loadedSettings;
                    state = loadedState;
                    statusMessage = "Loaded Minecraft tags for " + loadedState.getMinecraftUserId();
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                LOGGER.warn("NekoNameTags UI load failed: {}", ex.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    statusMessage = "Load failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-FabricUI-Load").start();
    }

    private void toggleMinecraftSync() {
        if (busy) {
            return;
        }
        saveLocalSettings();
        busy = true;
        boolean nextValue = webSettings == null || !webSettings.isTurnOnMinecraft();
        statusMessage = "Updating Minecraft sync...";
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftWebClient client = newClient();
                NekoMinecraftWebSettings updated = client.updateSettings(nextValue);
                NekoMinecraftWebState loadedState = client.fetchState();
                MinecraftClient.getInstance().execute(() -> {
                    webSettings = updated;
                    state = loadedState;
                    statusMessage = "Minecraft sync is now " + (updated.isTurnOnMinecraft() ? "ON" : "OFF");
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                LOGGER.warn("NekoNameTags UI sync toggle failed: {}", ex.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    statusMessage = "Sync update failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-FabricUI-Sync").start();
    }

    private void createTag() {
        if (busy) {
            return;
        }
        if (tagNameBox.getText().trim().isEmpty()) {
            statusMessage = "Tag text is required.";
            return;
        }
        if (sizeBox.getText().trim().isEmpty()) {
            statusMessage = "Size is required.";
            return;
        }
        runMutation("Creating Minecraft tag...", client -> client.createTag(
            tagNameBox.getText().trim(),
            bigTextBox.getText().trim(),
            sizeBox.getText().trim(),
            selectedHasAnimation,
            selectedAnimationType
        ));
    }

    private void updateSelected() {
        if (busy || selectedId < 0) {
            return;
        }
        if (selectionKind == SelectionKind.NORMAL) {
            runMutation("Updating Minecraft tag...", client -> client.updateTag(
                selectedId,
                tagNameBox.getText().trim(),
                sizeBox.getText().trim(),
                selectedHasAnimation,
                selectedAnimationType,
                selectedActive
            ));
            return;
        }
        if (selectionKind == SelectionKind.BIG) {
            runMutation("Updating Minecraft big tag...", client -> client.updateBigTag(
                selectedId,
                bigTextBox.getText().trim(),
                selectedActive
            ));
        }
    }

    private void deleteSelected() {
        if (busy || selectedId < 0) {
            return;
        }
        if (selectionKind == SelectionKind.NORMAL) {
            runMutation("Deleting Minecraft tag...", client -> client.deleteTag(selectedId));
            return;
        }
        if (selectionKind == SelectionKind.BIG) {
            runMutation("Deleting Minecraft big tag...", client -> client.deleteBigTag(selectedId));
        }
    }

    private void runMutation(String workingMessage, MutationAction action) {
        saveLocalSettings();
        busy = true;
        statusMessage = workingMessage;
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftWebClient client = newClient();
                action.run(client);
                requestBaseReload();
                NekoMinecraftWebSettings loadedSettings = client.fetchSettings();
                NekoMinecraftWebState loadedState = client.fetchState();
                MinecraftClient.getInstance().execute(() -> {
                    webSettings = loadedSettings;
                    state = loadedState;
                    clearSelection();
                    statusMessage = "Minecraft data updated.";
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                LOGGER.warn("NekoNameTags UI mutation failed: {}", ex.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    statusMessage = "Update failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-FabricUI-Mutation").start();
    }

    private NekoMinecraftWebClient newClient() {
        return new NekoMinecraftWebClient(baseUrlBox.getText().trim(), apiKeyBox.getText().trim());
    }

    private void requestBaseReload() {
        try {
            Class<?> apiClass = Class.forName("uk.co.nekosunevr.nekonametags.fabric.NekoNameTagsFabricApi");
            Method method = apiClass.getMethod("requestReload");
            method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void cycleAnimation() {
        if ("".equals(selectedAnimationType)) {
            selectedAnimationType = "rainbow";
            selectedHasAnimation = true;
        } else if ("rainbow".equals(selectedAnimationType)) {
            selectedAnimationType = "typing";
            selectedHasAnimation = true;
        } else {
            selectedAnimationType = "";
            selectedHasAnimation = false;
        }
        refreshButtons();
    }

    private void applySelection(RowEntry row) {
        selectionKind = row.kind;
        selectedId = row.id;
        selectedActive = row.active;
        if (row.kind == SelectionKind.NORMAL && row.tag != null) {
            tagNameBox.setText(row.tag.getCleanText());
            sizeBox.setText(row.tag.getSize());
            selectedHasAnimation = row.tag.isHasAnimation();
            selectedAnimationType = row.tag.getAnimationType();
        }
        if (row.kind == SelectionKind.BIG && row.bigTag != null) {
            bigTextBox.setText(row.bigTag.getText());
            selectedHasAnimation = false;
            selectedAnimationType = "";
        }
        refreshButtons();
    }

    private void clearSelection() {
        selectionKind = SelectionKind.NONE;
        selectedId = -1;
        selectedActive = true;
        selectedHasAnimation = false;
        selectedAnimationType = "";
        tagNameBox.setText("");
        bigTextBox.setText("");
        sizeBox.setText("1");
        refreshButtons();
    }

    private void refreshButtons() {
        String syncLabel = webSettings != null && webSettings.isTurnOnMinecraft() ? "Minecraft Sync: ON" : "Minecraft Sync: OFF";
        syncToggleButton.setMessage(Text.literal(syncLabel));
        animationButton.setMessage(Text.literal("Animation: " + ("".equals(selectedAnimationType) ? "None" : selectedAnimationType)));
        activeToggleButton.setMessage(Text.literal("Selected Active: " + (selectedActive ? "ON" : "OFF")));
        boolean hasSelection = selectedId >= 0;
        updateButton.active = !busy && hasSelection;
        deleteButton.active = !busy && hasSelection;
        syncToggleButton.active = !busy;
        animationButton.active = !busy;
        activeToggleButton.active = !busy && hasSelection;
    }

    private List<RowEntry> buildRows() {
        List<RowEntry> rows = new ArrayList<RowEntry>();
        if (state == null) {
            return rows;
        }
        for (NekoMinecraftWebTag tag : state.getTags()) {
            rows.add(RowEntry.normal(tag));
        }
        for (NekoMinecraftWebBigTag bigTag : state.getBigTags()) {
            rows.add(RowEntry.big(bigTag));
        }
        return rows;
    }

    private enum SelectionKind {
        NONE,
        NORMAL,
        BIG
    }

    private interface MutationAction {
        void run(NekoMinecraftWebClient client) throws Exception;
    }

    private static final class RowEntry {
        private final SelectionKind kind;
        private final int id;
        private final boolean active;
        private final String label;
        private final NekoMinecraftWebTag tag;
        private final NekoMinecraftWebBigTag bigTag;

        private RowEntry(SelectionKind kind, int id, boolean active, String label, NekoMinecraftWebTag tag, NekoMinecraftWebBigTag bigTag) {
            this.kind = kind;
            this.id = id;
            this.active = active;
            this.label = label;
            this.tag = tag;
            this.bigTag = bigTag;
        }

        private static RowEntry normal(NekoMinecraftWebTag tag) {
            return new RowEntry(
                SelectionKind.NORMAL,
                tag.getId(),
                tag.isActive(),
                "[Tag] " + tag.getCleanText() + " | size=" + tag.getSize() + " | anim=" + (tag.isHasAnimation() ? tag.getAnimationType() : "none"),
                tag,
                null
            );
        }

        private static RowEntry big(NekoMinecraftWebBigTag bigTag) {
            return new RowEntry(
                SelectionKind.BIG,
                bigTag.getId(),
                bigTag.isActive(),
                "[Big] " + bigTag.getText(),
                null,
                bigTag
            );
        }
    }
}
