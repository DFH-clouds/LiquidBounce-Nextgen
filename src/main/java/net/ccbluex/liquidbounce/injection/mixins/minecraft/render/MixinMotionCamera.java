package net.ccbluex.liquidbounce.injection.mixins.minecraft.render;

import net.ccbluex.liquidbounce.features.module.modules.render.ModuleMotionCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, remap = false)
public abstract class MixinMotionCamera {

    @Shadow
    private Vec3 position;

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void onAlignWithEntityReturn(float tickDelta, CallbackInfo ci) {
        ModuleMotionCamera module = ModuleMotionCamera.INSTANCE;
        if (!module.getRunning()) return;

        Vec3 target = this.position;

        Vec3 smoothed = module.smooth(target, tickDelta);

        this.position = smoothed;
    }
}
