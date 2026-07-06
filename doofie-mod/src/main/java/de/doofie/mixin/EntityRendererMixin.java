package de.doofie.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "renderLabelIfPresent", at = @At("TAIL"))
    private void doofie$renderBadge(S state, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue,
                                    CameraRenderState cameraState, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.getPerspective().isFirstPerson()) return;

        Text badge = Text.literal("D ").formatted(Formatting.GOLD)
            .append(Text.literal("DOOFIE").formatted(Formatting.WHITE));

        // Render badge 0.5 units above the entity's bounding box
        queue.submitLabel(matrices, new Vec3d(0.0, state.height + 0.5, 0.0),
            0x4C000000, badge, false, 0xFFFFAA00, 64.0, cameraState);
    }
}
