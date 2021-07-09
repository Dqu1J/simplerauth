package com.dqu.simplerauth.managers;

import com.dqu.simplerauth.AuthMod;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CacheManager {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PATH = FabricLoader.getInstance().getConfigDir().resolve("simplerauth-cache.json").toString();
    private static final File DBFILE = new File(PATH);
    private static JsonObject db = new JsonObject();

    public static void loadCache() {
        if (!DBFILE.exists()) {
            db.addProperty("version", VERSION);

            db.add("minecraft-account-cache", new JsonArray());

            saveCache();
        }

        try {
            BufferedReader bufferedReader = Files.newReader(DBFILE, StandardCharsets.UTF_8);
            db = GSON.fromJson(bufferedReader, JsonObject.class);
        } catch (Exception e) {
            AuthMod.LOGGER.error(e);
        }
    }

    private static void saveCache() {
        try {
            BufferedWriter bufferedWriter = Files.newWriter(DBFILE, StandardCharsets.UTF_8);
            String json = GSON.toJson(db);
            bufferedWriter.write(json);
            bufferedWriter.close();
        } catch (Exception e) {
            AuthMod.LOGGER.error(e);
        }
    }

    public static JsonArray getMinecraftAccounts() {
        return db.getAsJsonArray("minecraft-account-cache");
    }

    @Nullable
    public static JsonObject getMinecraftAccount(String username) {
        removeExpired();
        JsonArray minecraftAccounts = getMinecraftAccounts();
        for (int i = 0; i < minecraftAccounts.size(); ++i) {
            JsonObject account = minecraftAccounts.get(i).getAsJsonObject();
            if (account.get("username").getAsString().equals(username)) {
                return account;
            }
        }

        return null;
    }

    public static void addMinecraftAccount(String username, String onlineUuid) {
        String offlineUuid = PlayerEntity.getOfflinePlayerUuid(username).toString();
        onlineUuid = formatUuid(onlineUuid);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("username", username);
        jsonObject.addProperty("online-uuid", onlineUuid);
        jsonObject.addProperty("offline-uuid", offlineUuid);
        jsonObject.addProperty("timestamp", LocalDateTime.now().toString());

        getMinecraftAccounts().add(jsonObject);
        saveCache();
    }

    private static void removeExpired() {
        JsonArray minecraftAccounts = getMinecraftAccounts();
        Collection<JsonObject> invalidAccounts = Sets.newHashSet();
        for (int i = 0; i < minecraftAccounts.size(); ++i) {
            JsonObject account = minecraftAccounts.get(i).getAsJsonObject();
            LocalDateTime parsedTimestamp;
            try {
                parsedTimestamp = LocalDateTime.parse(account.get("timestamp").getAsString());
            } catch (Exception e) {
                continue;
            }
            boolean valid = Duration.between(parsedTimestamp, LocalDateTime.now()).toDays() <= 14;
            if (!valid) {
                invalidAccounts.add(account);
            }
        }

        for (JsonObject account : invalidAccounts) {
            minecraftAccounts.remove(account);
        }

        saveCache();
    }

    private static String formatUuid(String uuid) {
        // Check if UUID doesn't have dashes
        Pattern pattern = Pattern.compile("^[a-z0-9]{32}$");
        Matcher matcher = pattern.matcher(uuid);
        if (!matcher.matches()) return uuid;

        return String.format("%s-%s-%s-%s-%s", uuid.substring(0, 8), uuid.substring(8, 12), uuid.substring(12, 16), uuid.substring(16, 20), uuid.substring(20));
    }
}