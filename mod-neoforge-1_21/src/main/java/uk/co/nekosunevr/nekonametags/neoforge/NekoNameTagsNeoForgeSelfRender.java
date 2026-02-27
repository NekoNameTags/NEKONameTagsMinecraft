package uk.co.nekosunevr.nekonametags.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import uk.co.nekosunevr.nekonametags.core.ParsedTagLine;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "nekonametags", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
final class NekoNameTagsNeoForgeSelfRender {
    private NekoNameTagsNeoForgeSelfRender() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!NekoNameTagsNeoForgeClient.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) {
            return;
        }
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return;
        }

        List<ParsedTagLine> lines = NekoNameTagsNeoForgeClient.getSelfLines();
        if (lines.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double x = Mth.lerp(partialTick, self.xo, self.getX());
        double y = Mth.lerp(partialTick, self.yo, self.getY()) + self.getBbHeight() + 0.6D;
        double z = Mth.lerp(partialTick, self.zo, self.getZ());

        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x - camX, y - camY, z - camZ);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        long now = System.currentTimeMillis();
        List<FormattedCharSequence> renderedLines = new ArrayList<FormattedCharSequence>(lines.size());
        List<Float> ratios = new ArrayList<Float>(lines.size());
        float totalHeight = 0.0f;
        for (ParsedTagLine line : lines) {
            net.minecraft.network.chat.Component component = NekoNameTagsNeoForgeClient.buildStyledLineComponent(line, now);
            if (component == null) {
                continue;
            }
            float ratio = Math.max(0.7f, Math.min(3.0f, line.getSize() / 16.0f));
            renderedLines.add(component.getVisualOrderText());
            ratios.add(ratio);
            totalHeight += (10.0f * ratio) + 2.0f;
        }
        if (renderedLines.isEmpty()) {
            poseStack.popPose();
            return;
        }

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float yCursor = -totalHeight;
        for (int i = 0; i < renderedLines.size(); i++) {
            FormattedCharSequence line = renderedLines.get(i);
            float ratio = ratios.get(i);

            poseStack.pushPose();
            float baseScale = 0.025F;
            float lineScale = baseScale * ratio;
            poseStack.scale(-lineScale, -lineScale, lineScale);

            Matrix4f matrix = poseStack.last().pose();
            float xOffset = -mc.font.width(line) / 2.0F;
            float yOffset = yCursor / ratio;
            mc.font.drawInBatch(line, xOffset, yOffset, 0xFFFFFF, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
            poseStack.popPose();

            yCursor += (10.0f * ratio) + 2.0f;
        }
        bufferSource.endBatch();
        poseStack.popPose();
    }
}
