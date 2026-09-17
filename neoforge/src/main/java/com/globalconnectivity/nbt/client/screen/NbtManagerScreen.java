package com.globalconnectivity.nbt.client.screen;

import com.globalconnectivity.nbt.NbtFileStore;
import com.globalconnectivity.nbt.ServerListConfig;
import com.globalconnectivity.nbt.net.CloudApi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main UI with three tabs:
 * - Local Files: browse the nbtfiles folder, import via drag & drop, upload to the cloud
 * - Cloud Download: list the active server's files and download them
 * - Settings: manage cloud servers; the official server (47.103.169.249) is preconfigured
 */
public final class NbtManagerScreen extends Screen {
    private static final int TAB_LOCAL = 0;
    private static final int TAB_CLOUD = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int LIST_W = 320;
    private static final int ITEM_H = 26;

    private static boolean dropHandlerInstalled = false;

    private int tab = TAB_LOCAL;
    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    private List<CloudApi.CloudFile> cloudFiles = null;
    private String pendingServerName = "";
    private String pendingServerUrl = "";

    private NbtListWidget fileList;
    private NbtListWidget serverList;
    private Button uploadButton;
    private Button deleteButton;
    private Button downloadButton;
    private EditBox serverNameBox;
    private EditBox serverUrlBox;

    public NbtManagerScreen() {
        super(Component.translatable("gui.gc.title"));
    }

    /* ------------------------------------------------- layout */

    private int listX() {
        return this.width / 2 - LIST_W / 2;
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private void switchTab(int newTab) {
        if (newTab == this.tab) {
            return;
        }
        if (this.tab == TAB_SETTINGS && serverNameBox != null && serverUrlBox != null) {
            this.pendingServerName = serverNameBox.getValue();
            this.pendingServerUrl = serverUrlBox.getValue();
        }
        this.tab = newTab;
        this.status = Component.empty();
        rebuild();
    }

    @Override
    protected void init() {
        installDropHandler();
        int tabsX = this.width / 2 - 158;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(tabTitle(i), b -> switchTab(idx))
                    .bounds(tabsX + i * 108, 22, 100, 20)
                    .build());
        }
        switch (this.tab) {
            case TAB_CLOUD -> initCloudTab();
            case TAB_SETTINGS -> initSettingsTab();
            default -> initLocalTab();
        }
    }

    private static Component tabTitle(int i) {
        return Component.translatable(switch (i) {
            case TAB_CLOUD -> "gui.gc.tab.cloud";
            case TAB_SETTINGS -> "gui.gc.tab.settings";
            default -> "gui.gc.tab.local";
        });
    }

    /* ------------------------------------------------- local tab */

    private void initLocalTab() {
        int y = 52;
        int h = this.height - y - 60;
        fileList = new NbtListWidget(this.minecraft, listX(), y, LIST_W, h, ITEM_H, this::onLocalSelected);
        for (NbtFileStore.LocalFile f : NbtFileStore.list()) {
            fileList.addRow(f.name(), humanSize(f.sizeBytes()), f.name(), 0xFFFFFFFF);
        }
        addRenderableWidget(fileList);

        int by = this.height - 28;
        int bx = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.translatable("gui.gc.open_folder"), b -> openFolder())
                .bounds(bx, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.gc.refresh"), b -> rebuild())
                .bounds(bx + 98, by, 56, 20).build());
        uploadButton = addRenderableWidget(Button.builder(Component.translatable("gui.gc.upload"), b -> uploadSelected())
                .bounds(bx + 162, by, 110, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("gui.gc.delete"), b -> deleteSelected())
                .bounds(bx + 280, by, 56, 20).build());
        updateActionButtons();
    }

    private void onLocalSelected(NbtListWidget.Row row, boolean doubleClick) {
        updateActionButtons();
        if (doubleClick) {
            uploadSelected();
        }
    }

    private void openFolder() {
        try {
            NbtFileStore.ensureFolder();
            Util.getPlatform().openFile(NbtFileStore.folder().toFile());
        } catch (Exception e) {
            setStatus(Component.translatable("gui.gc.status.folder_error", String.valueOf(e)), 0xFFFF5555);
        }
    }

    private void uploadSelected() {
        String name = fileList == null ? null : fileList.selectedValue();
        if (name == null) {
            setStatus(Component.translatable("gui.gc.status.no_selection"), 0xFFFF5555);
            return;
        }
        Path path = NbtFileStore.resolveExisting(name);
        if (path == null) {
            setStatus(Component.translatable("gui.gc.status.upload_failed", name), 0xFFFF5555);
            return;
        }
        ServerListConfig.ServerEntry server = ServerListConfig.get().selected();
        setStatus(Component.translatable("gui.gc.status.uploading", name), 0xFFAAAAAA);
        CloudApi.uploadFile(server.url, name, path).whenComplete((v, err) -> onUi(() -> {
            if (err != null) {
                setStatus(Component.translatable("gui.gc.status.upload_failed", rootMessage(err)), 0xFFFF5555);
            } else {
                setStatus(Component.translatable("gui.gc.status.uploaded", name), 0xFF55FF55);
            }
        }));
    }

    private void deleteSelected() {
        String name = fileList == null ? null : fileList.selectedValue();
        if (name == null) {
            setStatus(Component.translatable("gui.gc.status.no_selection"), 0xFFFF5555);
            return;
        }
        if (NbtFileStore.delete(name)) {
            setStatus(Component.translatable("gui.gc.status.deleted", name), 0xFF55FF55);
            rebuild();
        } else {
            setStatus(Component.translatable("gui.gc.status.delete_failed", name), 0xFFFF5555);
        }
    }

    /* ------------------------------------------------- cloud tab */

    private void initCloudTab() {
        int y = 62;
        int h = this.height - y - 60;
        fileList = new NbtListWidget(this.minecraft, listX(), y, LIST_W, h, ITEM_H, this::onCloudSelected);
        addRenderableWidget(fileList);
        if (cloudFiles != null) {
            fillCloudList();
        } else {
            setStatus(Component.translatable("gui.gc.status.loading"), 0xFFAAAAAA);
        }

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.gc.refresh"), b -> refreshCloud(true))
                .bounds(this.width / 2 - 77, by, 56, 20).build());
        downloadButton = addRenderableWidget(Button.builder(Component.translatable("gui.gc.download"), b -> downloadSelected())
                .bounds(this.width / 2 + 21, by, 90, 20).build());
        updateActionButtons();
        refreshCloud(false);
    }

    private void onCloudSelected(NbtListWidget.Row row, boolean doubleClick) {
        updateActionButtons();
        if (doubleClick) {
            downloadSelected();
        }
    }

    private void fillCloudList() {
        List<NbtListWidget.RowData> rows = new ArrayList<>();
        if (cloudFiles != null) {
            for (CloudApi.CloudFile f : cloudFiles) {
                rows.add(new NbtListWidget.RowData(f.name(), humanSize(f.sizeBytes()), f.name(), 0xFFFFFFFF));
            }
        }
        fileList.setEntries(rows);
        updateActionButtons();
    }

    private void refreshCloud(boolean manual) {
        ServerListConfig.ServerEntry server = ServerListConfig.get().selected();
        if (manual) {
            setStatus(Component.translatable("gui.gc.status.loading"), 0xFFAAAAAA);
        }
        CloudApi.listFiles(server.url).whenComplete((files, err) -> onUi(() -> {
            if (err != null) {
                cloudFiles = List.of();
                setStatus(Component.translatable("gui.gc.status.fetch_failed", rootMessage(err)), 0xFFFF5555);
            } else {
                cloudFiles = files;
                setStatus(Component.translatable("gui.gc.status.loaded", files.size()), 0xFF55FF55);
            }
            if (this.tab == TAB_CLOUD) {
                fillCloudList();
            }
        }));
    }

    private void downloadSelected() {
        String name = fileList == null ? null : fileList.selectedValue();
        if (name == null) {
            setStatus(Component.translatable("gui.gc.status.no_selection"), 0xFFFF5555);
            return;
        }
        ServerListConfig.ServerEntry server = ServerListConfig.get().selected();
        setStatus(Component.translatable("gui.gc.status.downloading", name), 0xFFAAAAAA);
        CloudApi.downloadFile(server.url, name, name).whenComplete((p, err) -> onUi(() -> {
            if (err != null) {
                setStatus(Component.translatable("gui.gc.status.download_failed", rootMessage(err)), 0xFFFF5555);
            } else {
                setStatus(Component.translatable("gui.gc.status.downloaded", name), 0xFF55FF55);
            }
        }));
    }

    /* ------------------------------------------------- settings tab */

    private void initSettingsTab() {
        serverNameBox = new EditBox(this.font, listX(), 58, LIST_W, 18, Component.translatable("gui.gc.name_hint"));
        serverNameBox.setMaxLength(40);
        serverNameBox.setValue(pendingServerName);
        serverUrlBox = new EditBox(this.font, listX(), 80, LIST_W, 18, Component.translatable("gui.gc.url_hint"));
        serverUrlBox.setMaxLength(160);
        serverUrlBox.setValue(pendingServerUrl);
        addRenderableWidget(serverNameBox);
        addRenderableWidget(serverUrlBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.gc.add_server"), b -> addServer())
                .bounds(this.width / 2 - 94, 104, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.gc.test_connection"), b -> testConnection())
                .bounds(this.width / 2 + 4, 104, 90, 20).build());

        int ly = 138;
        int lh = this.height - ly - 60;
        serverList = new NbtListWidget(this.minecraft, listX(), ly, LIST_W, lh, ITEM_H, this::onServerSelected);
        addRenderableWidget(serverList);
        addRenderableWidget(Button.builder(Component.translatable("gui.gc.remove_server"), b -> removeCurrentServer())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
        refreshServerList();
        this.setInitialFocus(serverNameBox);
    }

    private void refreshServerList() {
        ServerListConfig cfg = ServerListConfig.get();
        List<NbtListWidget.RowData> rows = new ArrayList<>();
        for (int i = 0; i < cfg.servers.size(); i++) {
            ServerListConfig.ServerEntry s = cfg.servers.get(i);
            boolean active = i == cfg.selectedServer;
            String title = active
                    ? s.name + " " + Component.translatable("gui.gc.current_suffix").getString()
                    : s.name;
            rows.add(new NbtListWidget.RowData(title, s.url, String.valueOf(i), active ? 0xFF55FF55 : 0xFFFFFFFF));
        }
        serverList.setEntries(rows);
    }

    private void onServerSelected(NbtListWidget.Row row, boolean doubleClick) {
        try {
            int idx = Integer.parseInt(row.data.value());
            ServerListConfig cfg = ServerListConfig.get();
            if (idx >= 0 && idx < cfg.servers.size() && idx != cfg.selectedServer) {
                cfg.selectedServer = idx;
                ServerListConfig.save();
                cloudFiles = null;
                refreshServerList();
                setStatus(Component.translatable("gui.gc.status.server_switched", cfg.selected().name), 0xFF55FF55);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void addServer() {
        String name = serverNameBox.getValue().trim();
        String url = serverUrlBox.getValue().trim();
        if (name.isEmpty() || url.isEmpty()) {
            setStatus(Component.translatable("gui.gc.status.invalid_input"), 0xFFFF5555);
            return;
        }
        try {
            CloudApi.normalizeBaseUrl(url);
        } catch (Exception e) {
            setStatus(Component.translatable("gui.gc.status.invalid_url"), 0xFFFF5555);
            return;
        }
        ServerListConfig cfg = ServerListConfig.get();
        cfg.servers.add(new ServerListConfig.ServerEntry(name, url));
        cfg.selectedServer = cfg.servers.size() - 1;
        ServerListConfig.save();
        cloudFiles = null;
        serverNameBox.setValue("");
        serverUrlBox.setValue("");
        pendingServerName = "";
        pendingServerUrl = "";
        refreshServerList();
        setStatus(Component.translatable("gui.gc.status.server_added", name), 0xFF55FF55);
    }

    private void removeCurrentServer() {
        ServerListConfig cfg = ServerListConfig.get();
        if (cfg.servers.size() <= 1) {
            return;
        }
        cfg.servers.remove(cfg.selectedServer);
        if (cfg.selectedServer >= cfg.servers.size()) {
            cfg.selectedServer = cfg.servers.size() - 1;
        }
        if (cfg.servers.isEmpty()) {
            cfg.servers.add(new ServerListConfig.ServerEntry(ServerListConfig.OFFICIAL_NAME, ServerListConfig.OFFICIAL_URL));
            cfg.selectedServer = 0;
        }
        ServerListConfig.save();
        cloudFiles = null;
        refreshServerList();
        setStatus(Component.translatable("gui.gc.status.server_removed"), 0xFF55FF55);
    }

    private void testConnection() {
        ServerListConfig.ServerEntry server = ServerListConfig.get().selected();
        setStatus(Component.translatable("gui.gc.status.loading"), 0xFFAAAAAA);
        CloudApi.ping(server.url).whenComplete((info, err) -> onUi(() -> {
            if (err != null) {
                setStatus(Component.translatable("gui.gc.status.ping_failed", rootMessage(err)), 0xFFFF5555);
            } else {
                setStatus(Component.translatable("gui.gc.status.ping_ok", info), 0xFF55FF55);
            }
        }));
    }

    /* ------------------------------------------------- drag & drop import */

    private static void installDropHandler() {
        if (dropHandlerInstalled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        dropHandlerInstalled = true;
        GLFW.glfwSetDropCallback(mc.getWindow().getWindow(), GLFWDropCallback.create((window, count, names) -> {
            Minecraft m = Minecraft.getInstance();
            if (m == null || !(m.screen instanceof NbtManagerScreen screen)) {
                return;
            }
            var pointers = MemoryUtil.memLongBuffer(names, count);
            for (int i = 0; i < count; i++) {
                String path = MemoryUtil.memUTF8(pointers.get(i));
                m.execute(() -> screen.importDropped(path));
            }
        }));
    }

    private void importDropped(String path) {
        Path p = Path.of(path);
        String fileName = p.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            setStatus(Component.translatable("gui.gc.status.not_nbt", fileName), 0xFFFF5555);
            return;
        }
        String stored = NbtFileStore.importFile(p);
        if (stored == null) {
            setStatus(Component.translatable("gui.gc.status.import_failed", fileName), 0xFFFF5555);
            return;
        }
        setStatus(Component.translatable("gui.gc.status.imported", stored), 0xFF55FF55);
        if (this.tab != TAB_SETTINGS) {
            rebuild();
        }
    }

    /* ------------------------------------------------- shared helpers */

    private void updateActionButtons() {
        boolean has = fileList != null && fileList.selectedValue() != null;
        if (uploadButton != null) {
            uploadButton.active = has;
        }
        if (deleteButton != null) {
            deleteButton.active = has;
        }
        if (downloadButton != null) {
            downloadButton.active = has;
        }
    }

    private void setStatus(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    private void onUi(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(r);
        } else {
            r.run();
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        return m == null ? c.getClass().getSimpleName() : m;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f MB", bytes / 1048576.0);
    }

    /* ------------------------------------------------- rendering */

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        int tabsX = this.width / 2 - 158;
        graphics.fill(tabsX + this.tab * 108, 43, tabsX + this.tab * 108 + 100, 45, 0xFF4080FF);

        switch (this.tab) {
            case TAB_CLOUD -> {
                ServerListConfig.ServerEntry s = ServerListConfig.get().selected();
                graphics.drawCenteredString(this.font, Component.translatable("gui.gc.server_line", s.name, s.url),
                        this.width / 2, 48, 0xFFA0A0FF);
                if (fileList != null && fileList.children().isEmpty()) {
                    Component hint = cloudFiles == null
                            ? Component.translatable("gui.gc.status.loading")
                            : Component.translatable("gui.gc.list.empty");
                    graphics.drawCenteredString(this.font, hint, this.width / 2, 74, 0xFF888888);
                }
            }
            case TAB_SETTINGS -> {
                graphics.drawString(this.font, Component.translatable("gui.gc.add_server_label"), listX(), 47, 0xFFCCCCCC);
                graphics.drawString(this.font, Component.translatable("gui.gc.click_to_activate"), listX(), 127, 0xFF888888);
            }
            default -> {
                graphics.drawCenteredString(this.font, Component.translatable("gui.gc.drop_hint"),
                        this.width / 2, this.height - 52, 0xFF888888);
                if (fileList != null && fileList.children().isEmpty()) {
                    graphics.drawCenteredString(this.font, Component.translatable("gui.gc.list.empty"),
                            this.width / 2, 70, 0xFF888888);
                }
            }
        }

        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 42, this.statusColor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
