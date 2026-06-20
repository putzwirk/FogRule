package com.putzwirk.fogrule;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.putzwirk.fogrule.cozy.CozinessEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FogRule.MODID)
public class FogRuleCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fogrule")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("skipTime")
                        .then(Commands.argument("ticks", LongArgumentType.longArg(1L))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        long ticksToSkip = LongArgumentType.getLong(context, "ticks");
                                        int affectedChunks = CozinessEngine.forceTimeSkipForPlayer(player, ticksToSkip);
                                        source.sendSuccess(() -> Component.literal(
                                                "§a[FogRule] Simulated "
                                                        + ticksToSkip + " ticks passing across " + affectedChunks + " chunks! Evaluating decay..."), true);
                                        return 1;
                                    } else {
                                        source.sendFailure(Component.literal("This command must be run by a player."));
                                        return 0;
                                    }
                                })
                        )
                )
        );
    }
}