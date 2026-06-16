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
        leftList.add("[Fog Rule]");
        leftList.add(String.format("  Coziness Range: %.1f blocks", FogHandler.currentClearanceEnd));
    }
}