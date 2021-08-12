package com.dqu.simplerauth.commands;

import com.dqu.simplerauth.AuthMod;
import com.dqu.simplerauth.managers.ConfigManager;
import com.dqu.simplerauth.managers.DbManager;
import com.dqu.simplerauth.managers.LangManager;
import com.dqu.simplerauth.PlayerObject;
import com.dqu.simplerauth.api.event.PlayerAuthEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LoginCommand {
    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("login")
            .then(argument("password", StringArgumentType.word())
                .executes(ctx -> login(ctx))
            )
        );
    }

    private static int login(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        PlayerObject playerObject = AuthMod.playerManager.get(player);

        if (playerObject.isAuthenticated()) {
            throw new SimpleCommandExceptionType(LangManager.getLiteralText("command.login.alreadylogged")).create();
        }

        String password = StringArgumentType.getString(ctx, "password");
        String username = player.getEntityName();
        
        if (!isPasswordCorrect(username, password)) {
            // TODO: fails and kick after x fails
            PlayerAuthEvents.PLAYER_LOGIN_FAIL.invoker().onPlayerLoginFail(player, 1);
            player.networkHandler.disconnect(LangManager.getLiteralText("command.general.notmatch"));
            return 0;
        }
        playerObject.authenticate();
        PlayerAuthEvents.PLAYER_LOGIN.invoker().onPlayerLogin(player, "loginCommand");
        if (ConfigManager.getBoolean("sessions-enabled")) {
            DbManager.sessionCreate(player.getEntityName(), player.getIp());
        }
        ctx.getSource().sendFeedback(LangManager.getLiteralText("command.general.authenticated"), false);
        if (ConfigManager.getBoolean("hide-position")) {
            Vec3d pos = DbManager.getPosition(username);
            if (pos != null)
                player.requestTeleport(pos.getX(), pos.getY(), pos.getZ());
        }
        return 1;
    }

    private static boolean isPasswordCorrect(String username, String password) throws CommandSyntaxException {
        switch (ConfigManager.getAuthType()) {
            case "local" -> {
                // Local Password Authentication
                if (!DbManager.isPlayerRegistered(username)) {
                    throw new SimpleCommandExceptionType(LangManager.getLiteralText("command.login.notregistered")).create();
                }
                if (DbManager.isPasswordCorrect(username, password)) {
                    return true;
                }
            }
            case "global" -> {
                // Global Password Authentication
                if (password.equals(ConfigManager.getString("global-password"))) {
                    return true;
                }
            }
            default -> {
                // Config setup is wrong
                throw new SimpleCommandExceptionType(LangManager.getLiteralText("config.incorrect")).create();
            }
        }
        return false;
    }
}
