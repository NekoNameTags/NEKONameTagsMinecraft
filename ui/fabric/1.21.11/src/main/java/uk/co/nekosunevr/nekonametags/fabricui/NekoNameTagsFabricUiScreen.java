package uk.co.nekosunevr.nekonametags.fabricui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
    private static Method drawTextMethod;
    private static boolean drawTextLookupDone;

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
        int rootLeft = getRootLeft();
        int rootTop = getRootTop();
        int rootWidth = getRootWidth();
        int leftWidth = 360;
        int gap = 14;
        int rightLeft = rootLeft + leftWidth + gap;
        int rightWidth = rootWidth - leftWidth - gap;
        int fieldWidth = leftWidth - 24;
        int actionButtonWidth = fieldWidth;
        int smallButtonWidth = (rightWidth - 24 - 12) / 2;
        int topButtonWidth = 130;

        baseUrlBox = addDrawableChild(new TextFieldWidget(this.textRenderer, rootLeft + 12, rootTop + 52, fieldWidth, 20, Text.literal("Web API Base URL")));
        baseUrlBox.setMaxLength(256);
        baseUrlBox.setText(settings.getWebApiBaseUrl());
        apiKeyBox = addDrawableChild(new TextFieldWidget(this.textRenderer, rootLeft + 12, rootTop + 96, fieldWidth, 20, Text.literal("API Key")));
        apiKeyBox.setMaxLength(128);
        apiKeyBox.setText(settings.getWebApiKey());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save Connection"), button -> {
            saveLocalSettings();
            statusMessage = "Saved local API settings.";
            refreshButtons();
        }).dimensions(rootLeft + 12, rootTop + 136, actionButtonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh From WEB"), button -> {
            saveLocalSettings();
            loadMinecraftState();
        }).dimensions(rootLeft + 12, rootTop + 160, actionButtonWidth, 20).build());

        syncToggleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Minecraft Sync: OFF"), button -> toggleMinecraftSync())
            .dimensions(rootLeft + 12, rootTop + 200, actionButtonWidth, 20).build());

        tagNameBox = addDrawableChild(new TextFieldWidget(this.textRenderer, rightLeft + 12, rootTop + 52, rightWidth - 24, 20, Text.literal("Tag Text")));
        tagNameBox.setMaxLength(256);
        bigTextBox = addDrawableChild(new TextFieldWidget(this.textRenderer, rightLeft + 12, rootTop + 96, rightWidth - 24, 20, Text.literal("Big Tag Text")));
        bigTextBox.setMaxLength(256);
        sizeBox = addDrawableChild(new TextFieldWidget(this.textRenderer, rightLeft + 12, rootTop + 140, 76, 20, Text.literal("Size")));
        sizeBox.setMaxLength(8);
        sizeBox.setText("1");

        animationButton = addDrawableChild(ButtonWidget.builder(Text.literal("Animation: None"), button -> cycleAnimation())
            .dimensions(rightLeft + 100, rootTop + 140, 156, 20).build());
        activeToggleButton = addDrawableChild(ButtonWidget.builder(Text.literal("Selected Active: ON"), button -> {
            selectedActive = !selectedActive;
            refreshButtons();
        }).dimensions(rightLeft + 268, rootTop + 140, rightWidth - 280, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Create Minecraft Tag"), button -> createTag())
            .dimensions(rightLeft + 12, rootTop + 182, smallButtonWidth, 20).build());
        updateButton = addDrawableChild(ButtonWidget.builder(Text.literal("Update Selected"), button -> updateSelected())
            .dimensions(rightLeft + 24 + smallButtonWidth, rootTop + 182, smallButtonWidth, 20).build());
        deleteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete Selected"), button -> deleteSelected())
            .dimensions(rightLeft + 12, rootTop + 206, rightWidth - 24, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
            .dimensions(rootLeft + rootWidth - topButtonWidth, rootTop + 12, topButtonWidth, 20).build());

        refreshButtons();
        if (settings.hasWebApiKey()) {
            loadMinecraftState();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int rootLeft = getRootLeft();
        int rootTop = getRootTop();
        int rootWidth = getRootWidth();
        int leftWidth = 360;
        int gap = 14;
        int rightLeft = rootLeft + leftWidth + gap;
        int rightWidth = rootWidth - leftWidth - gap;
        int listLeft = rootLeft;
        int listTop = rootTop + 256;
        int listWidth = rootWidth;
        int rowHeight = 22;

        context.fillGradient(0, 0, this.width, this.height, 0xA0141723, 0xC00A0B12);
        context.fill(rootLeft - 8, rootTop - 8, rootLeft + rootWidth + 8, this.height - 18, 0x38000000);

        drawPanel(context, rootLeft, rootTop, leftWidth, 228, 0xC4171D2A, 0xD0222938);
        drawPanel(context, rightLeft, rootTop, rightWidth, 228, 0xC4171E24, 0xD0222C34);
        drawPanel(context, listLeft, listTop, listWidth, this.height - listTop - 28, 0xB8141821, 0xD01B222D);

        drawTextCompat(context, "NEKONAMETAGS", rootLeft + 16, rootTop + 14, 0xFFF2F6FF);
        drawTextCompat(context, "Minecraft tag manager", rootLeft + 16, rootTop + 28, 0xFF8FA4C0);

        drawTextCompat(context, "WEB API Base URL", rootLeft + 12, rootTop + 40, 0xFF8FA4C0);
        drawTextCompat(context, "Personal API Key", rootLeft + 12, rootTop + 84, 0xFF8FA4C0);

        drawTextCompat(context, "Tag Editor", rightLeft + 16, rootTop + 16, 0xFF7FDBFF);
        drawTextCompat(context, "Normal Tag Text", rightLeft + 12, rootTop + 40, 0xFF8FA4C0);
        drawTextCompat(context, "Big Tag Text", rightLeft + 12, rootTop + 84, 0xFF8FA4C0);
        drawTextCompat(context, "Style", rightLeft + 12, rootTop + 128, 0xFF8FA4C0);

        drawStatusChip(context, rootLeft + 12, rootTop + 228, rootWidth - 24, shortStatusMessage(), busy ? 0xFFE9B44C : isStatusError() ? 0xFFE06464 : 0xFF62D59B);

        drawTextCompat(context, "Tag Library", listLeft + 16, listTop + 12, 0xFFF2F6FF);
        drawTextCompat(context, "Select a row to edit or remove it", listLeft + 16, listTop + 26, 0xFF8FA4C0);

        super.render(context, mouseX, mouseY, delta);

        List<RowEntry> rows = buildRows();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = listTop + 42 + (i * rowHeight);
            if (rowY + rowHeight > this.height - 36) {
                break;
            }
            RowEntry row = rows.get(i);
            boolean selected = row.kind == selectionKind && row.id == selectedId;
            int background = selected ? 0xD02C436F : 0x7A1A2029;
            int accent = row.active ? 0xFF62D59B : 0xFFE06464;
            context.fill(listLeft + 10, rowY, listLeft + listWidth - 10, rowY + rowHeight - 4, background);
            context.fill(listLeft + 10, rowY, listLeft + 14, rowY + rowHeight - 4, accent);
            drawTextCompat(context, fitText(row.label, listWidth - 36), listLeft + 22, rowY + 6, 0xFFF2F6FF);
        }
    }

    private void drawTextCompat(DrawContext context, String text, int x, int y, int color) {
        Method method = getDrawTextMethod(context);
        if (method == null) {
            return;
        }
        try {
            if (method.getParameterCount() == 6) {
                method.invoke(context, this.textRenderer, text, x, y, color, Boolean.FALSE);
                return;
            }
            method.invoke(context, this.textRenderer, text, x, y, color);
        } catch (ReflectiveOperationException ex) {
            LOGGER.warn("NekoNameTags UI text render failed: {}", ex.getMessage());
        }
    }

    private static Method getDrawTextMethod(DrawContext context) {
        if (drawTextLookupDone) {
            return drawTextMethod;
        }
        drawTextLookupDone = true;
        for (Method method : context.getClass().getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 6
                && TextRenderer.class.isAssignableFrom(parameterTypes[0])
                && parameterTypes[1] == String.class
                && parameterTypes[2] == Integer.TYPE
                && parameterTypes[3] == Integer.TYPE
                && parameterTypes[4] == Integer.TYPE
                && parameterTypes[5] == Boolean.TYPE) {
                drawTextMethod = method;
                return drawTextMethod;
            }
            if (parameterTypes.length == 5
                && TextRenderer.class.isAssignableFrom(parameterTypes[0])
                && parameterTypes[1] == String.class
                && parameterTypes[2] == Integer.TYPE
                && parameterTypes[3] == Integer.TYPE
                && parameterTypes[4] == Integer.TYPE) {
                drawTextMethod = method;
                return drawTextMethod;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<RowEntry> rows = buildRows();
        int listLeft = getRootLeft();
        int listWidth = getRootWidth();
        int rowHeight = 22;
        int rowStartY = getRootTop() + 256 + 42;

        for (int i = 0; i < rows.size(); i++) {
            int rowY = rowStartY + (i * rowHeight);
            int rowBottom = rowY + rowHeight - 4;
            if (rowBottom > this.height - 36) {
                break;
            }
            if (mouseX >= listLeft + 10 && mouseX <= listLeft + listWidth - 10 && mouseY >= rowY && mouseY <= rowBottom) {
                setFocused(null);
                applySelection(rows.get(i));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

    private String shortStatusMessage() {
        String status = statusMessage == null ? "" : statusMessage.trim();
        if (status.isEmpty()) {
            return "Waiting for refresh";
        }
        if (status.length() > 88) {
            return status.substring(0, 85) + "...";
        }
        return status;
    }

    private boolean isStatusError() {
        String status = statusMessage == null ? "" : statusMessage.toLowerCase();
        return status.contains("failed") || status.contains("invalid") || status.contains("error");
    }

    private int getRootLeft() {
        return (this.width - getRootWidth()) / 2;
    }

    private int getRootTop() {
        return 20;
    }

    private int getRootWidth() {
        return Math.min(980, this.width - 32);
    }

    private void drawPanel(DrawContext context, int x, int y, int width, int height, int topColor, int bottomColor) {
        context.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x55FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66000000);
        context.fill(x + width - 1, y, x + width, y + height, 0x66000000);
    }

    private void drawStatusChip(DrawContext context, int x, int y, int width, String text, int accentColor) {
        context.fill(x, y, x + width, y + 22, 0xA0121720);
        context.fill(x, y, x + 5, y + 22, accentColor);
        drawTextCompat(context, text, x + 12, y + 7, 0xFFF2F6FF);
    }

    private String fitText(String value, int maxWidth) {
        if (this.textRenderer.getWidth(value) <= maxWidth) {
            return value;
        }
        String trimmed = value;
        while (trimmed.length() > 4 && this.textRenderer.getWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
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
