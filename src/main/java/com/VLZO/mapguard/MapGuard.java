package com.VLZO.mapguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;


public class MapGuard extends JavaPlugin {

    // config-backed fields
    private int normalSaveIntervalTicks;
    private int normalBackupIntervalTicks;
    private int normalMaxBackups;
    private List<String> normalDisabledWorlds = Collections.emptyList();

    private int slimeSaveIntervalTicks;
    private int slimeBackupIntervalTicks;
    private int slimeMaxBackups;
    private List<String> slimeDisabledWorlds = Collections.emptyList();
    private String slimeworldPath;

    private boolean crashDetectionEnabled;
    private boolean autoBackupEnabled;
    private boolean autoSaveEnabled;
    private boolean slimeBackupEnabled;
    private boolean showSaveMessage;
    private String saveMessageText;
    private boolean debugMode;

    private File backupFolder;
    private volatile boolean backupRunning = false;

    // protection listener reference for reload
    private MapGuardProtection protectionListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getLogger().info("MapGuard enabled");

        backupFolder = new File(getDataFolder(), "backups");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            getLogger().warning("Could not create backup folder: " + backupFolder.getPath());
        }

        PluginManager pm = getServer().getPluginManager();
        protectionListener = new MapGuardProtection(this);
        pm.registerEvents(protectionListener, this);

        try {
            MapGuardCommand handler = new MapGuardCommand(this);
            PluginCommand cmd = getCommand("mapguard");
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            } else {
                getLogger().warning("Command 'mapguard' not declared in plugin.yml");
            }
        } catch (Throwable t) {
            debug("Command registration skipped: " + t.getMessage());
        }

        startTasks();

        if (crashDetectionEnabled) {
            File crashFlag = new File(getDataFolder(), "last-run.flag");
            if (!crashFlag.exists()) {
                getLogger().warning("Possible previous crash detected; consider restoring backup.");
            }
            try {
                crashFlag.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create crash flag file: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        File crashFlag = new File(getDataFolder(), "last-run.flag");
        if (crashFlag.exists() && !crashFlag.delete()) {
            getLogger().warning("Failed to delete crash flag file: " + crashFlag.getPath());
        }
        stopTasks();
        getLogger().info("MapGuard disabled");
    }

    private void loadConfigValues() {
        autoSaveEnabled = getConfig().getBoolean("enable-auto-save", true);
        autoBackupEnabled = getConfig().getBoolean("enable-auto-backup", true);
        crashDetectionEnabled = getConfig().getBoolean("enable-crash-detection", true);
        showSaveMessage = getConfig().getBoolean("show-save-message", false);
        saveMessageText = getConfig().getString("save-message-text", "&2[MG] &fWorld saved successfully!");
        debugMode = getConfig().getBoolean("debug-mode", false);

        ConfigurationSection normal = getConfig().getConfigurationSection("normal-worlds");
        if (normal == null) normal = getConfig();
        int normalSaveMinutes = normal.getInt("save-interval-minutes", 5);
        int normalBackupMinutes = normal.getInt("backup-interval-minutes", 15);
        normalSaveIntervalTicks = 20 * 60 * Math.max(1, normalSaveMinutes);
        normalBackupIntervalTicks = 20 * 60 * Math.max(1, normalBackupMinutes);
        normalMaxBackups = Math.max(1, normal.getInt("max-backups", 5));
        List<String> nd = normal.getStringList("backup-disabled-worlds");
        if (nd != null) normalDisabledWorlds = nd;

        ConfigurationSection slime = getConfig().getConfigurationSection("slime-worlds");
        if (slime == null) slime = getConfig();
        int slimeSaveMinutes = slime.getInt("save-interval-minutes", 0);
        int slimeBackupMinutes = slime.getInt("backup-interval-minutes", 30);
        slimeSaveIntervalTicks = 20 * 60 * Math.max(0, slimeSaveMinutes);
        slimeBackupIntervalTicks = 20 * 60 * Math.max(1, slimeBackupMinutes);
        slimeMaxBackups = Math.max(1, slime.getInt("max-backups", 3));
        List<String> sd = slime.getStringList("backup-disabled-worlds");
        if (sd != null) slimeDisabledWorlds = sd;
        slimeBackupEnabled = slime.getBoolean("enable-slimeworld-backup", true);

        // Auto-discover slimeworld-path if config value missing or empty
        String rawPath = slime.getString("slimeworld-path", "").trim();
        if (rawPath.isEmpty()) {
            File defaultDir = new File(Bukkit.getWorldContainer(), "slime_worlds");
            slimeworldPath = defaultDir.getAbsolutePath();
            getLogger().info("No slimeworld-path defined; using default: " + slimeworldPath);
        } else {
            File customDir = new File(rawPath);
            if (!customDir.isAbsolute()) customDir = new File(Bukkit.getWorldContainer(), rawPath);
            slimeworldPath = customDir.getAbsolutePath();
            getLogger().info("Using slimeworld-path from config: " + slimeworldPath);
        }
    }

    public void startTasks() {
        if (autoSaveEnabled) {
            if (normalSaveIntervalTicks > 0) {
                Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    @Override public void run() { saveAllNormalWorlds(); }
                }, normalSaveIntervalTicks, normalSaveIntervalTicks);
            }
            if (slimeSaveIntervalTicks > 0) {
                Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    @Override public void run() { /* reserved for future slime saves */ }
                }, slimeSaveIntervalTicks, slimeSaveIntervalTicks);
            }
        }

        if (autoBackupEnabled) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                @Override public void run() { createBackupNormalWorlds(); }
            }, normalBackupIntervalTicks, normalBackupIntervalTicks);

            if (slimeBackupEnabled) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                    @Override public void run() { backupSlimeWorldFiles(); }
                }, slimeBackupIntervalTicks, slimeBackupIntervalTicks);
            }
        }
    }

    public void stopTasks() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadConfigValues();
        stopTasks();
        startTasks();
        getLogger().info("MapGuard config reloaded");
        if (protectionListener != null) protectionListener.reload();
    }

    // ---------------- Backup / Save implementations ----------------

    public void createBackupNormalWorlds() {
        if (backupRunning) return;
        backupRunning = true;
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            for (World world : Bukkit.getWorlds()) {
                if (normalDisabledWorlds != null && normalDisabledWorlds.contains(world.getName())) continue;
                File worldFolder = world.getWorldFolder();
                File worldBackupFolder = new File(backupFolder, world.getName());
                if (!worldBackupFolder.exists() && !worldBackupFolder.mkdirs()) {
                    getLogger().warning("Failed to create backup folder for: " + world.getName());
                    continue;
                }
                File zipFile = new File(worldBackupFolder, timestamp + ".zip");
                try {
                    ZipUtils.zipFolder(worldFolder, zipFile);
                    getLogger().info("Backup created for world: " + world.getName());
                } catch (Exception ex) {
                    getLogger().warning("Backup failed for " + world.getName() + ": " + ex.getMessage());
                }
                enforceMaxBackups(worldBackupFolder, normalMaxBackups);
            }
        } finally {
            backupRunning = false;
        }
    }

    private void enforceMaxBackups(File folder, int max) {
        File[] files = folder.listFiles();
        if (files == null || files.length <= max) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        int remove = files.length - max;
        for (int i = 0; i < remove; i++) {
            if (!files[i].delete()) getLogger().warning("Failed to delete old backup: " + files[i].getName());
        }
    }

    public void createBackupForWorldInternal(World world) {
        if (world == null) return;
        if (normalDisabledWorlds != null && normalDisabledWorlds.contains(world.getName())) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File worldFolder = world.getWorldFolder();
        File worldBackupFolder = new File(backupFolder, world.getName());
        if (!worldBackupFolder.exists() && !worldBackupFolder.mkdirs()) {
            getLogger().warning("Failed to create backup folder for: " + world.getName());
            return;
        }
        File zipFile = new File(worldBackupFolder, timestamp + ".zip");
        try {
            ZipUtils.zipFolder(worldFolder, zipFile);
            getLogger().info("Manual backup created: " + zipFile.getName());
            enforceMaxBackups(worldBackupFolder, normalMaxBackups);
        } catch (Exception ex) {
            getLogger().warning("Manual backup failed for " + world.getName() + ": " + ex.getMessage());
        }
    }

    public void createBackupForSlimeInternal(String slimeName) {
        List<String> names = getSlimeWorldNames();
        if (!names.contains(slimeName)) {
            debug("Slime name not found: " + slimeName);
            return;
        }
        File source = getSlimeFileForName(slimeName);
        if (source == null) return;
        File slimeBackupRoot = new File(backupFolder, "slimeworlds");
        if (!slimeBackupRoot.exists() && !slimeBackupRoot.mkdirs()) {
            getLogger().warning("Could not create slime backup root: " + slimeBackupRoot.getPath());
            return;
        }
        File slimeWorldFolder = new File(slimeBackupRoot, slimeName);
        if (!slimeWorldFolder.exists() && !slimeWorldFolder.mkdirs()) {
            getLogger().warning("Could not create slime world backup folder: " + slimeWorldFolder.getPath());
            return;
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backupFile = new File(slimeWorldFolder, slimeName + "_" + timestamp + ".slime");
        try {
            Files.copy(source.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Slime backup created: " + backupFile.getName());
            enforceMaxBackups(slimeWorldFolder, slimeMaxBackups);
        } catch (IOException e) {
            getLogger().warning("Slime backup failed: " + e.getMessage());
        }
    }

    public void backupSlimeWorldFiles() {
        File dir = new File(slimeworldPath);
        if (!dir.isAbsolute()) dir = new File(Bukkit.getWorldContainer(), slimeworldPath);
        if (!dir.exists() || !dir.isDirectory()) {
            debug("Slime folder not found: " + dir.getPath());
            return;
        }
        File slimeBackupRoot = new File(backupFolder, "slimeworlds");
        if (!slimeBackupRoot.exists() && !slimeBackupRoot.mkdirs()) {
            getLogger().warning("Could not create slime backup folder: " + slimeBackupRoot.getPath());
            return;
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File[] slimeFiles = dir.listFiles(new java.io.FilenameFilter() {
            @Override public boolean accept(File d, String n) { return n.toLowerCase().endsWith(".slime"); }
        });
        if (slimeFiles == null || slimeFiles.length == 0) {
            debug("No .slime files found in: " + dir.getPath());
            return;
        }
        for (File file : slimeFiles) {
            String baseName = file.getName().replaceAll("(?i)\\.slime$", "");
            if (slimeDisabledWorlds != null && slimeDisabledWorlds.contains(baseName)) continue;
            File slimeWorldFolder = new File(slimeBackupRoot, baseName);
            if (!slimeWorldFolder.exists() && !slimeWorldFolder.mkdirs()) {
                getLogger().warning("Could not create folder for slime backups: " + slimeWorldFolder.getPath());
                continue;
            }
            File backupFile = new File(slimeWorldFolder, baseName + "_" + timestamp + ".slime");
            try {
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Slime backup created: " + backupFile.getName());
                enforceMaxBackups(slimeWorldFolder, slimeMaxBackups);
            } catch (IOException e) {
                getLogger().warning("Slime backup failed: " + e.getMessage());
            }
        }
    }

    public void saveAllNormalWorlds() {
        for (World world : Bukkit.getWorlds()) {
            if (normalDisabledWorlds != null && normalDisabledWorlds.contains(world.getName())) continue;
            try { world.save(); } catch (Exception ex) { getLogger().warning("Failed to save world " + world.getName()); }
        }
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            try { p.saveData(); } catch (Exception ex) { getLogger().warning("Failed to save player " + p.getName()); }
        }
        if (showSaveMessage) Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', saveMessageText));
    }

    // ---------------- Utilities ----------------

    public List<String> getSlimeWorldNames() {
        File dir = new File(slimeworldPath);
        if (!dir.isAbsolute()) dir = new File(Bukkit.getWorldContainer(), slimeworldPath);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            @Override public boolean accept(File d, String n) { return n.toLowerCase().endsWith(".slime"); }
        });
        if (files == null) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        for (File f : files) out.add(f.getName().replaceAll("(?i)\\.slime$", ""));
        return out;
    }

    private File getSlimeFileForName(String name) {
        File dir = new File(slimeworldPath);
        if (!dir.isAbsolute()) dir = new File(Bukkit.getWorldContainer(), slimeworldPath);
        File candidate = new File(dir, name + ".slime");
        if (candidate.exists() && candidate.isFile()) return candidate;
        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            @Override public boolean accept(File d, String n) { return n.toLowerCase().endsWith(".slime"); }
        });
        if (files != null) for (File f : files) if (f.getName().equalsIgnoreCase(name + ".slime")) return f;
        return null;
    }

    private void debug(String msg) { if (debugMode) getLogger().info("[MapGuard DEBUG] " + msg); }

    // ---------------- Slime preloader (console dispatch) ----------------

    public void preloadSlimeWorldAndTeleport(final Player player, final String slimeWorldName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(slimeWorldName, "slimeWorldName");

        World loaded = Bukkit.getWorld(slimeWorldName);
        if (loaded != null) {
            player.teleport(loaded.getSpawnLocation());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aTeleported to slime world: " + slimeWorldName));
            return;
        }

        // check .slime file existence for clearer feedback
        File slimeFile = getSlimeFileForName(slimeWorldName);
        if (slimeFile == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cSlime file not found for: " + slimeWorldName + ". Expected in: " + slimeworldPath));
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("SlimeWorldManager") != null) {
            debug("Dispatching slime load command for: " + slimeWorldName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "slime load " + slimeWorldName + " read-write");

            new BukkitRunnable() {
                @Override public void run() {
                    World w = Bukkit.getWorld(slimeWorldName);
                    if (w != null) {
                        player.teleport(w.getSpawnLocation());
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aTeleported to slime world: " + slimeWorldName));
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&cFailed to load slime world: " + slimeWorldName + ". Ensure SWM is installed and file is valid."));
                    }
                }
            }.runTaskLater(this, 20L); // 1 second delay
            return;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&cSlimeWorldManager not found on server; cannot preload slime world: " + slimeWorldName));
    }

    // ---------------- Public wrappers used by commands ----------------

    public void createBackup() {
        createBackupNormalWorlds();
        if (slimeBackupEnabled) backupSlimeWorldFiles();
    }

    public void createBackupForWorld(World world) { createBackupForWorldInternal(world); }

    public void createBackupForSlime(String slimeName) { createBackupForSlimeInternal(slimeName); }

    public void saveWorld(World world) {
        if (world == null) return;
        try {
            world.save();
            getLogger().info("Saved world: " + world.getName());
        } catch (Exception ex) {
            getLogger().warning("Failed to save world " + world.getName() + ": " + ex.getMessage());
        }
    }

    public boolean saveWorldForSlime(String slimeName) {
        debug("saveWorldForSlime requested for: " + slimeName);
        return false;
    }

    // ---------------- Simple getters ----------------

    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public List<String> getNormalDisabledWorlds() { return normalDisabledWorlds; }
    public List<String> getSlimeDisabledWorlds() { return slimeDisabledWorlds; }
    public String getSlimeworldPath() { return slimeworldPath; }
    public int getNormalBackupIntervalTicks() { return normalBackupIntervalTicks; }
    public int getSlimeBackupIntervalTicks() { return slimeBackupIntervalTicks; }
    public int getNormalMaxBackups() { return normalMaxBackups; }
    public int getSlimeMaxBackups() { return slimeMaxBackups; }
}