package com.putzwirk.fogrule.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void preventFogMobBurning(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob) (Object) this;

        if (mob.getPersistentData().contains("FogRule_ImmuneToSun")) {
            cir.setReturnValue(false);
        }
    }
}