package uk.co.nekosunevr.nekonametags.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;

import java.util.ArrayList;
import java.util.List;

final class NekoNameTagsFabricSelfRender {
    private static boolean registered;

    private NekoNameTagsFabricSelfRender() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!NekoNameTagsFabricClient.isEnabled()) {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                return;
            }
            if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                return;
            }

            List<ParsedTagLine> lines = NekoNameTagsFabricClient.getSelfLines();
            if (lines.isEmpty()) {
                return;
            }

            Camera camera = context.camera();
            float tickDelta = context.tickCounter().getTickDelta(false);
            double x = MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX());
            double y = MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY()) + mc.player.getHeight() + 0.6D;
            double z = MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ());

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(x - camera.getPos().x, y - camera.getPos().y, z - camera.getPos().z);

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            matrices.multiply(dispatcher.getRotation());
            matrices.scale(-1.0F, -1.0F, 1.0F);

            long now = System.currentTimeMillis();
            List<OrderedText> renderedLines = new ArrayList<OrderedText>(lines.size());
            List<Float> ratios = new ArrayList<Float>(lines.size());
            float totalHeight = 0.0f;
            for (ParsedTagLine line : lines) {
                Text styled = NekoNameTagsFabricClient.buildStyledLineText(line, now);
                if (styled == null) {
                    continue;
                }
                float ratio = Math.max(0.7f, Math.min(3.0f, line.getSize() / 16.0f));
                renderedLines.add(styled.asOrderedText());
                ratios.add(ratio);
                totalHeight += (10.0f * ratio) + 2.0f;
            }
            if (renderedLines.isEmpty()) {
                matrices.pop();
                return;
            }

            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            float yCursor = -totalHeight;
            for (int i = 0; i < renderedLines.size(); i++) {
                OrderedText line = renderedLines.get(i);
                float ratio = ratios.get(i);

                matrices.push();
                float baseScale = 0.025F;
                float lineScale = baseScale * ratio;
                matrices.scale(lineScale, lineScale, lineScale);

                float xOffset = -mc.textRenderer.getWidth(line) / 2.0F;
                float yOffset = yCursor / ratio;
                mc.textRenderer.draw(line, xOffset, yOffset, 0xFFFFFF, false, matrices.peek().getPositionMatrix(), immediate, net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                matrices.pop();

                yCursor += (10.0f * ratio) + 2.0f;
            }

            immediate.draw();
            matrices.pop();
        });
    }
}
