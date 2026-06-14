package com.putzwirk.fogrule;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "fogrule", value = Dist.CLIENT)
public class DebugMenuHandler {

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        List<String> leftList = event.getLeft();
        
        leftList.add("");
        leftList.add("[Fog System Settings]");
        leftList.add(String.format("  Coziness Level: %.2f", FogHandler.rawCozinessLevel));
        leftList.add(String.format("  Safe Radius: %.2f blocks", FogHandler.lastCalculatedSafeRadius));
        leftList.add(String.format("  Clearance Range: %.2f blocks", FogHandler.activeClearanceRange));
        leftList.add(String.format("  Danger Intensity: %.2f%%", FogHandler.currentDanger * 100f));
        leftList.add(String.format("  Anchor Position: X=%.1f, Z=%.1f", FogHandler.clientCozyX, FogHandler.clientCozyZ));
    }
}