package com.globalconnectivity.nbt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side list of cloud servers, persisted at
 * {@code config/globalconnectivity-servers.json}. Ships with one entry:
 * the official server (47.103.169.249); players can add their own.
 */
public final class ServerListConfig {
    public static final String OFFICIAL_NAME = "官方服务器";
    public static final String OFFICIAL_URL = "http://47.103.169.249:8080";

    public static final class ServerEntry {
        public String name;
        public String url;

        public ServerEntry() {}

        public ServerEntry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public List<ServerEntry> servers = new ArrayList<>();
    public int selectedServer = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ServerListConfig instance;

    public static synchronized ServerListConfig get() {
        if (instance == null) {
            instance = load();
            if (instance == null) {
                instance = new ServerListConfig();
                instance.servers.add(new ServerEntry(OFFICIAL_NAME, OFFICIAL_URL));
                instance.selectedServer = 0;
            }
        }
        return instance;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("globalconnectivity-servers.json");
    }

    private static ServerListConfig load() {
        try {
            if (!Files.isRegularFile(file())) {
                return null;
            }
            ServerListConfig cfg = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), ServerListConfig.class);
            if (cfg == null || cfg.servers == null || cfg.servers.isEmpty()) {
                return null;
            }
            for (ServerEntry s : cfg.servers) {
                if (s.name == null || s.url == null) {
                    return null;
                }
            }
            if (cfg.selectedServer < 0 || cfg.selectedServer >= cfg.servers.size()) {
                cfg.selectedServer = 0;
            }
            return cfg;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(get()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public ServerEntry selected() {
        if (selectedServer < 0 || selectedServer >= servers.size()) {
            selectedServer = 0;
        }
        return servers.get(selectedServer);
    }
}
