package uk.co.nekosunevr.nekonametags.forgeui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.co.nekosunevr.nekonametags.core.NekoClientSettings;
import uk.co.nekosunevr.nekonametags.core.NekoMinecraftWebTag;
import uk.co.nekosunevr.nekonametags.uicore.NekoMinecraftUiRow;
import uk.co.nekosunevr.nekonametags.uicore.NekoMinecraftUiService;
import uk.co.nekosunevr.nekonametags.uicore.NekoMinecraftUiSnapshot;

import java.lang.reflect.Method;
import java.util.List;

final class NekoNameTagsForgeUiScreen extends Screen {
    private final Screen parent;
    private final NekoClientSettings settings;

    private EditBox baseUrlBox;
    private EditBox apiKeyBox;
    private EditBox tagNameBox;
    private EditBox bigTextBox;
    private EditBox sizeBox;

    private Button syncToggleButton;
    private Button animationButton;
    private Button activeToggleButton;
    private Button updateButton;
    private Button deleteButton;

    private NekoMinecraftUiSnapshot snapshot;
    private String statusMessage = "Load your Minecraft data with Refresh.";
    private boolean busy;
    private NekoMinecraftUiRow.Kind selectionKind = NekoMinecraftUiRow.Kind.NONE;
    private int selectedId = -1;
    private boolean selectedActive = true;
    private boolean selectedHasAnimation;
    private String selectedAnimationType = "";

    private NekoNameTagsForgeUiScreen(Screen parent) {
        super(Component.literal("NekoNameTags Minecraft Manager"));
        this.parent = parent;
        this.settings = NekoClientSettings.loadDefault();
    }

    static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new NekoNameTagsForgeUiScreen(mc.screen)));
    }

    @Override
    protected void init() {
        int left = 20;
        int top = 24;
        int fieldWidth = Math.min(320, this.width - 40);
        int buttonWidth = 150;

        baseUrlBox = addRenderableWidget(new EditBox(this.font, left, top + 12, fieldWidth, 20, Component.literal("Web API Base URL")));
        baseUrlBox.setValue(settings.getWebApiBaseUrl());
        apiKeyBox = addRenderableWidget(new EditBox(this.font, left, top + 44, fieldWidth, 20, Component.literal("API Key")));
        apiKeyBox.setValue(settings.getWebApiKey());

        addRenderableWidget(Button.builder(Component.literal("Save Local API Settings"), button -> {
            saveLocalSettings();
            statusMessage = "Saved local API settings.";
        }).bounds(left + fieldWidth + 10, top + 12, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh Minecraft Data"), button -> {
            saveLocalSettings();
            loadMinecraftState();
        }).bounds(left + fieldWidth + 10, top + 44, buttonWidth, 20).build());

        syncToggleButton = addRenderableWidget(Button.builder(Component.literal("Minecraft Sync: OFF"), button -> toggleMinecraftSync())
            .bounds(left, top + 76, buttonWidth, 20).build());

        tagNameBox = addRenderableWidget(new EditBox(this.font, left, top + 118, fieldWidth, 20, Component.literal("Tag Text")));
        bigTextBox = addRenderableWidget(new EditBox(this.font, left, top + 150, fieldWidth, 20, Component.literal("Big Tag Text")));
        sizeBox = addRenderableWidget(new EditBox(this.font, left, top + 182, 80, 20, Component.literal("Size")));
        sizeBox.setValue("1");

        animationButton = addRenderableWidget(Button.builder(Component.literal("Animation: None"), button -> cycleAnimation())
            .bounds(left + 90, top + 182, 130, 20).build());
        activeToggleButton = addRenderableWidget(Button.builder(Component.literal("Selected Active: ON"), button -> {
            selectedActive = !selectedActive;
            refreshButtons();
        }).bounds(left + 230, top + 182, 140, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Create Minecraft Tag"), button -> createTag())
            .bounds(left, top + 214, buttonWidth, 20).build());
        updateButton = addRenderableWidget(Button.builder(Component.literal("Update Selected"), button -> updateSelected())
            .bounds(left + 160, top + 214, buttonWidth, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete Selected"), button -> deleteSelected())
            .bounds(left + 320, top + 214, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(this.width - 90, this.height - 28, 70, 20).build());

        refreshButtons();
        if (settings.hasWebApiKey()) {
            loadMinecraftState();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 20, 8, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.literal("Web API Base URL"), 20, 26, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("API Key"), 20, 58, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Tag Text"), 20, 132, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Big Tag Text"), 20, 164, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Status: " + statusMessage), 20, this.height - 18, busy ? 0xFFFF55 : 0x55FF55, false);

        int listLeft = 20;
        int listTop = 248;
        int listWidth = this.width - 40;
        int rowHeight = 18;
        graphics.fill(listLeft, listTop - 4, listLeft + listWidth, this.height - 38, 0x66000000);
        graphics.drawString(this.font, Component.literal("Minecraft Tags"), listLeft + 4, listTop - 14, 0xFFFFFF, false);

        List<NekoMinecraftUiRow> rows = buildRows();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = listTop + (i * rowHeight);
            if (rowY + rowHeight > this.height - 40) {
                break;
            }
            NekoMinecraftUiRow row = rows.get(i);
            boolean selected = row.getKind() == selectionKind && row.getId() == selectedId;
            int background = selected ? 0xAA224466 : 0x55222222;
            graphics.fill(listLeft + 2, rowY, listLeft + listWidth - 2, rowY + rowHeight - 2, background);
            graphics.drawString(this.font, row.getLabel(), listLeft + 6, rowY + 5, row.isActive() ? 0xFFFFFF : 0xFF7777, false);
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
            List<NekoMinecraftUiRow> rows = buildRows();
            if (index >= 0 && index < rows.size()) {
                applySelection(rows.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void saveLocalSettings() {
        NekoMinecraftUiService.saveLocalSettings(settings, baseUrlBox.getValue(), apiKeyBox.getValue());
    }

    private void loadMinecraftState() {
        if (busy) {
            return;
        }
        if (apiKeyBox.getValue().trim().isEmpty()) {
            statusMessage = "Enter your WEB API key first.";
            return;
        }
        busy = true;
        statusMessage = "Loading Minecraft settings and tags...";
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftUiSnapshot loaded = NekoMinecraftUiService.loadSnapshot(baseUrlBox.getValue(), apiKeyBox.getValue());
                Minecraft.getInstance().execute(() -> {
                    snapshot = loaded;
                    statusMessage = "Loaded Minecraft tags for " + loaded.getState().getMinecraftUserId();
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Load failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-ForgeUI-Load").start();
    }

    private void toggleMinecraftSync() {
        if (busy) {
            return;
        }
        saveLocalSettings();
        busy = true;
        boolean nextValue = snapshot == null || snapshot.getSettings() == null || !snapshot.getSettings().isTurnOnMinecraft();
        statusMessage = "Updating Minecraft sync...";
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftUiSnapshot loaded = NekoMinecraftUiService.updateSync(baseUrlBox.getValue(), apiKeyBox.getValue(), nextValue);
                Minecraft.getInstance().execute(() -> {
                    snapshot = loaded;
                    statusMessage = "Minecraft sync is now " + (loaded.getSettings().isTurnOnMinecraft() ? "ON" : "OFF");
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Sync update failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-ForgeUI-Sync").start();
    }

    private void createTag() {
        if (busy) {
            return;
        }
        if (tagNameBox.getValue().trim().isEmpty()) {
            statusMessage = "Tag text is required.";
            return;
        }
        if (sizeBox.getValue().trim().isEmpty()) {
            statusMessage = "Size is required.";
            return;
        }
        runMutation(() -> NekoMinecraftUiService.createTag(
            baseUrlBox.getValue(),
            apiKeyBox.getValue(),
            tagNameBox.getValue().trim(),
            bigTextBox.getValue().trim(),
            sizeBox.getValue().trim(),
            selectedHasAnimation,
            selectedAnimationType
        ), "Creating Minecraft tag...");
    }

    private void updateSelected() {
        if (busy || selectedId < 0) {
            return;
        }
        if (selectionKind == NekoMinecraftUiRow.Kind.NORMAL) {
            runMutation(() -> NekoMinecraftUiService.updateTag(
                baseUrlBox.getValue(),
                apiKeyBox.getValue(),
                selectedId,
                tagNameBox.getValue().trim(),
                sizeBox.getValue().trim(),
                selectedHasAnimation,
                selectedAnimationType,
                selectedActive
            ), "Updating Minecraft tag...");
            return;
        }
        if (selectionKind == NekoMinecraftUiRow.Kind.BIG) {
            runMutation(() -> NekoMinecraftUiService.updateBigTag(
                baseUrlBox.getValue(),
                apiKeyBox.getValue(),
                selectedId,
                bigTextBox.getValue().trim(),
                selectedActive
            ), "Updating Minecraft big tag...");
        }
    }

    private void deleteSelected() {
        if (busy || selectedId < 0) {
            return;
        }
        if (selectionKind == NekoMinecraftUiRow.Kind.NORMAL) {
            runMutation(() -> NekoMinecraftUiService.deleteTag(baseUrlBox.getValue(), apiKeyBox.getValue(), selectedId), "Deleting Minecraft tag...");
            return;
        }
        if (selectionKind == NekoMinecraftUiRow.Kind.BIG) {
            runMutation(() -> NekoMinecraftUiService.deleteBigTag(baseUrlBox.getValue(), apiKeyBox.getValue(), selectedId), "Deleting Minecraft big tag...");
        }
    }

    private void runMutation(Mutation mutation, String workingMessage) {
        saveLocalSettings();
        busy = true;
        statusMessage = workingMessage;
        refreshButtons();
        new Thread(() -> {
            try {
                NekoMinecraftUiSnapshot loaded = mutation.run();
                requestBaseReload();
                Minecraft.getInstance().execute(() -> {
                    snapshot = loaded;
                    clearSelection();
                    statusMessage = "Minecraft data updated.";
                    busy = false;
                    refreshButtons();
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Update failed: " + ex.getMessage();
                    busy = false;
                    refreshButtons();
                });
            }
        }, "NekoNameTags-ForgeUI-Mutation").start();
    }

    private void requestBaseReload() {
        try {
            Class<?> apiClass = Class.forName("uk.co.nekosunevr.nekonametags.forge.NekoNameTagsForgeApi");
            Method method = apiClass.getMethod("requestReload");
            method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private List<NekoMinecraftUiRow> buildRows() {
        return snapshot == null ? List.of() : NekoMinecraftUiService.buildRows(snapshot.getState());
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

    private void applySelection(NekoMinecraftUiRow row) {
        selectionKind = row.getKind();
        selectedId = row.getId();
        selectedActive = row.isActive();
        if (row.getKind() == NekoMinecraftUiRow.Kind.NORMAL && row.getTag() != null) {
            NekoMinecraftWebTag tag = row.getTag();
            tagNameBox.setValue(tag.getCleanText());
            sizeBox.setValue(tag.getSize());
            selectedHasAnimation = tag.isHasAnimation();
            selectedAnimationType = tag.getAnimationType();
        }
        if (row.getKind() == NekoMinecraftUiRow.Kind.BIG && row.getBigTag() != null) {
            bigTextBox.setValue(row.getBigTag().getText());
            selectedHasAnimation = false;
            selectedAnimationType = "";
        }
        refreshButtons();
    }

    private void clearSelection() {
        selectionKind = NekoMinecraftUiRow.Kind.NONE;
        selectedId = -1;
        selectedActive = true;
        selectedHasAnimation = false;
        selectedAnimationType = "";
        tagNameBox.setValue("");
        bigTextBox.setValue("");
        sizeBox.setValue("1");
        refreshButtons();
    }

    private void refreshButtons() {
        String syncLabel = snapshot != null && snapshot.getSettings() != null && snapshot.getSettings().isTurnOnMinecraft() ? "Minecraft Sync: ON" : "Minecraft Sync: OFF";
        syncToggleButton.setMessage(Component.literal(syncLabel));
        animationButton.setMessage(Component.literal("Animation: " + ("".equals(selectedAnimationType) ? "None" : selectedAnimationType)));
        activeToggleButton.setMessage(Component.literal("Selected Active: " + (selectedActive ? "ON" : "OFF")));
        boolean hasSelection = selectedId >= 0;
        updateButton.active = !busy && hasSelection;
        deleteButton.active = !busy && hasSelection;
        syncToggleButton.active = !busy;
        animationButton.active = !busy;
        activeToggleButton.active = !busy && hasSelection;
    }

    @FunctionalInterface
    private interface Mutation {
        NekoMinecraftUiSnapshot run() throws Exception;
    }
}
