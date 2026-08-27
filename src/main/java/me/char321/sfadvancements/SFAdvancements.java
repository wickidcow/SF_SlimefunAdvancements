package me.char321.sfadvancements;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.libraries.dough.config.Config;
import me.char321.sfadvancements.api.AdvancementBuilder;
import me.char321.sfadvancements.api.AdvancementGroup;
import me.char321.sfadvancements.api.criteria.CriteriaTypes;
import me.char321.sfadvancements.core.AdvManager;
import me.char321.sfadvancements.core.AdvancementsItemGroup;
import me.char321.sfadvancements.core.command.SFACommand;
import me.char321.sfadvancements.core.criteria.completer.CriterionCompleter;
import me.char321.sfadvancements.core.criteria.completer.DefaultCompleters;
import me.char321.sfadvancements.core.gui.AdvGUIManager;
import me.char321.sfadvancements.core.registry.AdvancementsRegistry;
import me.char321.sfadvancements.core.tasks.AutoSaveTask;
import me.char321.sfadvancements.util.ConfigUtils;
import me.char321.sfadvancements.util.Utils;
import me.char321.sfadvancements.vanilla.VanillaHook;
import net.guizhanss.minecraft.guizhanlib.updater.GuizhanUpdater;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SFAdvancements extends JavaPlugin implements SlimefunAddon {
    private static SFAdvancements instance;
    private final AdvManager advManager = new AdvManager();
    private final AdvGUIManager guiManager = new AdvGUIManager();
    private final AdvancementsRegistry registry = new AdvancementsRegistry();
    private final VanillaHook vanillaHook = new VanillaHook();

    private Config config;
    private YamlConfiguration advancementConfig;
    private YamlConfiguration groupConfig;

    private boolean multiBlockCraftEvent = false;

    public SFAdvancements() {

    }

    @Override
    public void onEnable() {
        instance = this;

        if (!getServer().getPluginManager().isPluginEnabled("GuizhanLibPlugin")) {
            getLogger().log(Level.SEVERE, "This plugin requires GuizhanLibPlugin to run!");
            getLogger().log(Level.SEVERE, "Download it from: https://50l.cc/gzlib");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        config = new Config(this);
        enforceRecipeSafeConfig();

        detectCapabilities();

        autoUpdate();

        getCommand("sfadvancements").setExecutor(new SFACommand(this));

        // init gui
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        // init sf
        AdvancementsItemGroup.init(this);

        // init core
        DefaultCompleters.registerDefaultCompleters();
        CriteriaTypes.loadDefaultCriteria();

        info("Starting auto-save task...");
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new AutoSaveTask(), 6000L, 6000L);

        Metrics metrics = new Metrics(this, 14130);
        metrics.addCustomChart(new SimplePie("AdvancementAPI enabled",
                () -> config.getBoolean("use-advancements-api") ? "true" : "false"));

        // allow other plugins to register their criteria completers
        info("Waiting for server start...");
        Utils.runLater(() -> {
            info("Loading advancement groups from config...");
            loadGroups();
            info("Loading advancements from config...");
            loadAdvancements();

            if (config.getBoolean("use-advancements-api")) {
                vanillaHook.init();
            }
        }, 0L);

    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        try {
            advManager.save();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, e, () -> "Could not save advancements");
        }
    }

    private void detectCapabilities() {
        try {
            Class.forName("io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent");
            multiBlockCraftEvent = true;
        } catch (ClassNotFoundException e) {
            multiBlockCraftEvent = false;
        }
    }

    private void autoUpdate() {
        if (config.getBoolean("auto-update") && getDescription().getVersion().startsWith("Build")) {
            info("Checking for updates...");
            GuizhanUpdater.start(this, this.getFile(), "SlimefunGuguProject", "SlimefunAdvancements", "main");
        }
    }

    private void enforceRecipeSafeConfig() {
        if (config.getBoolean("reload-data-on-adv-remove")) {
            warn("Disabling reload-data-on-adv-remove because Bukkit.reloadData() can unregister runtime recipes. "
                    + "Slimefun Advancements now keeps recipe registration intact.");
            config.setValue("reload-data-on-adv-remove", false);
            config.save();
        }
    }

    public void reload() {
        config.reload();
        enforceRecipeSafeConfig();
        advManager.getPlayerMap().clear();
        registry.getAdvancements().clear();
        registry.getAdvancementGroups().clear();
        registry.getCompleters().values().forEach(CriterionCompleter::reload);

        loadGroups();
        loadAdvancements();

        if (config.getBoolean("use-advancements-api")) {
            vanillaHook.reload();
        }
    }

    public void loadGroups() {
        File groupFile = new File(getDataFolder(), "groups.yml");
        groupConfig = loadConfigWithBundledDefaults(groupFile, "groups.yml", "advancement group");
        for (String key : groupConfig.getKeys(false)) {
            String background = groupConfig.getString(key + ".background", "SLIME_BLOCK");
            ItemStack display = ConfigUtils.getItem(groupConfig, key + ".display");
            String frameType = groupConfig.getString(key + ".frame_type", "GOAL");
            AdvancementGroup group = new AdvancementGroup(key, display, frameType, background);
            group.register();
        }
    }

    public void loadAdvancements() {
        File advancementsFile = new File(getDataFolder(), "advancements.yml");
        advancementConfig = loadConfigWithBundledDefaults(advancementsFile, "advancements.yml", "advancement");
        migrateKnownAdvancementDefaults(advancementsFile);

        for (String key : advancementConfig.getKeys(false)) {
            ConfigurationSection section = advancementConfig.getConfigurationSection(key);
            if (section == null) {
                warn("Advancement " + key + " is not a configuration section; skipping it");
                continue;
            }
            AdvancementBuilder builder = AdvancementBuilder.loadFromConfig(key, section);
            if (builder != null) {
                builder.register();
            }
        }
    }

    private YamlConfiguration loadConfigWithBundledDefaults(File file, String resourceName, String label) {
        if (!file.exists()) {
            saveResource(resourceName, false);
        }

        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        int restored = restoreMissingTopLevelDefaults(live, resourceName);
        if (restored <= 0) {
            return live;
        }

        try {
            backupBeforeDefaultRestore(file);
            live.save(file);
            info("Restored " + restored + " missing default " + (restored == 1 ? label : label + "s")
                    + " from the bundled " + resourceName + " without overwriting existing entries.");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save restored defaults to " + file.getName(), e);
        }
        return live;
    }

    private int restoreMissingTopLevelDefaults(YamlConfiguration live, String resourceName) {
        try (InputStream stream = getResource(resourceName)) {
            if (stream == null) {
                warn("Bundled default resource " + resourceName + " was not found");
                return 0;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            int restored = 0;
            for (String key : defaults.getKeys(false)) {
                if (live.contains(key)) {
                    continue;
                }

                ConfigurationSection source = defaults.getConfigurationSection(key);
                if (source != null) {
                    ConfigurationSection target = live.createSection(key);
                    copySection(source, target);
                } else {
                    live.set(key, defaults.get(key));
                }
                restored++;
            }
            return restored;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not read bundled defaults from " + resourceName, e);
            return 0;
        }
    }

    private void migrateKnownAdvancementDefaults(File advancementsFile) {
        String carbonadoFrame = advancementConfig.getString("carbonado.frame_type");
        if (carbonadoFrame == null || !carbonadoFrame.equalsIgnoreCase("CHALLENGER")) {
            return;
        }

        try {
            backupBeforeDefaultRestore(advancementsFile);
            advancementConfig.set("carbonado.frame_type", "CHALLENGE");
            advancementConfig.save(advancementsFile);
            info("Corrected legacy carbonado frame type CHALLENGER -> CHALLENGE.");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not migrate the legacy carbonado frame type", e);
        }
    }

    private static void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child != null) {
                copySection(child, target.createSection(key));
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    private void backupBeforeDefaultRestore(File file) throws IOException {
        Path backup = file.toPath().resolveSibling(file.getName() + ".pre-1.0.4.bak");
        if (!Files.exists(backup)) {
            Files.copy(file.toPath(), backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    @Nonnull
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Nullable
    @Override
    public String getBugTrackerURL() {
        return null;
    }

    public static SFAdvancements instance() {
        return instance;
    }

    public static AdvManager getAdvManager() {
        return instance.advManager;
    }

    public static AdvGUIManager getGuiManager() {
        return instance.guiManager;
    }

    public static AdvancementsRegistry getRegistry() {
        return instance.registry;
    }

    public static VanillaHook getVanillaHook() {
        return instance.vanillaHook;
    }

    public static Config getMainConfig() {
        return instance.config;
    }

    public YamlConfiguration getAdvancementConfig() {
        return advancementConfig;
    }

    public YamlConfiguration getGroupsConfig() {
        return groupConfig;
    }

    public boolean isMultiBlockCraftEvent() {
        return multiBlockCraftEvent;
    }

    public static Logger logger() {
        return instance.getLogger();
    }

    public static void info(String msg) {
        instance.getLogger().info(msg);
    }

    public static void warn(String msg) {
        instance.getLogger().warning(msg);
    }

    public static void error(String msg) {
        instance.getLogger().severe(msg);
    }

}
