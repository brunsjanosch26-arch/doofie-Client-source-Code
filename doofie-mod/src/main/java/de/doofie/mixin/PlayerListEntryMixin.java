package de.doofie.mixin;

import de.doofie.CapeManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void injectDoofieCape(CallbackInfoReturnable<SkinTextures> cir) {
        PlayerListEntry self = (PlayerListEntry)(Object)this;
        UUID uuid = self.getProfile().id();
        Identifier capeId = CapeManager.getCapeForPlayer(uuid);
        if (capeId == null) return;

        SkinTextures orig = cir.getReturnValue();
        AssetInfo.TextureAsset capeAsset = new net.minecraft.util.AssetInfo.TextureAssetInfo(capeId, capeId);
        cir.setReturnValue(new SkinTextures(
            orig.body(),
            capeAsset,
            orig.elytra(),
            orig.model(),
            orig.secure()
        ));
    }
}
