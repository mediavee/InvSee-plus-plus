package com.janboerman.invsee.spigot;

import com.janboerman.invsee.paper.AsyncTabCompleter;
import com.janboerman.invsee.spigot.api.InvseeAPI;
import com.janboerman.invsee.spigot.api.OfflinePlayerProvider;
import com.janboerman.invsee.spigot.api.logging.LogGranularity;
import com.janboerman.invsee.spigot.api.logging.LogOptions;
import com.janboerman.invsee.spigot.api.logging.LogTarget;
import com.janboerman.invsee.spigot.api.target.Target;
/*
import com.janboerman.invsee.spigot.multiverseinventories.MultiverseInventoriesHook;
import com.janboerman.invsee.spigot.multiverseinventories.MultiverseInventoriesSeeApi;
 */
import com.janboerman.invsee.spigot.api.template.Mirror;
import com.janboerman.invsee.spigot.perworldinventory.PerWorldInventoryHook;
import com.janboerman.invsee.spigot.perworldinventory.PerWorldInventorySeeApi;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class InvseePlusPlus extends JavaPlugin {

    private InvseeAPI api;
    private OfflinePlayerProvider offlinePlayerProvider;

    @Override
    public void onEnable() {
        //configuration
        saveDefaultConfig();

        //initialisation
        Setup setup = Setup.setup(this);
        this.api = setup.api();
        this.offlinePlayerProvider = setup.offlinePlayerProvider();

        //interop
        PerWorldInventoryHook pwiHook;
        //MultiverseInventoriesHook mviHook;
        if (offlinePlayerSupport() && (pwiHook = new PerWorldInventoryHook(this)).trySetup()) {
            if (pwiHook.managesEitherInventory()) {
                this.api = new PerWorldInventorySeeApi(this, api, pwiHook);
                getLogger().info("Enabled PerWorldInventory integration.");
            }
        }
//        else if (offlinePlayerSupport() && (mviHook = new MultiverseInventoriesHook(this)).trySetup()) {
//            this.api = new MultiverseInventoriesSeeApi(this, api, mviHook);
//            getLogger().info("Enabled Multiverse-Inventories integration.");
//        }
        // else if (MyWorlds)
        // else if (Separe-World-Items)

        //set configured values
        api.setOfflinePlayerSupport(offlinePlayerSupport());
        api.setUnknownPlayerSupport(unknownPlayerSupport());
        api.setMainInventoryTitle(this::getTitleForInventory);
        api.setEnderInventoryTitle(this::getTitleForEnderChest);
        api.setMainInventoryMirror(Mirror.forInventory(getInventoryTemplate()));
        api.setEnderInventoryMirror(Mirror.forEnderChest(getEnderChestTemplate()));
        api.setLogOptions(getLogOptions());

        //commands
        PluginCommand invseeCommand = getCommand("invsee");
        PluginCommand enderseeCommand = getCommand("endersee");

        invseeCommand.setExecutor(new InvseeCommandExecutor(this));
        enderseeCommand.setExecutor(new EnderseeCommandExecutor(this));

        InvseeTabCompleter tabCompleter = new InvseeTabCompleter(this);
        invseeCommand.setTabCompleter(tabCompleter);
        enderseeCommand.setTabCompleter(tabCompleter);

        //event listeners
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new SpectatorInventoryEditListener(), this);

        if (offlinePlayerSupport() && tabCompleteOfflinePlayers()) {
            try {
                Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent");
                pluginManager.registerEvents(new AsyncTabCompleter(this), this);
            } catch (ClassNotFoundException e) {
                getLogger().log(Level.WARNING, "InvSee++ is not running on a Paper API-enabled server.");
                getLogger().log(Level.WARNING, "Tab-completion for offline players will not work for all players!");
                getLogger().log(Level.WARNING, "See https://papermc.io/ for more information.");
            }
        }

        //TODO idea: shoulder look functionality. an admin will always see the same inventory that the target player sees.
        //TODO can I make it so that the bottom slots show the target player's inventory slots? would probably need to do some nms hacking

        //bStats
        int pluginId = 9309;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SimplePie("Back-end", () -> {
            if (this.api instanceof PerWorldInventorySeeApi) {
                return "PerWorldInventory";
//            } else if (this.api instanceof MultiverseInventoriesSeeApi) {
//                return "Multiverse-Inventories";
            }
            //else if: MyWorlds
            //else if: Separe-World-Items
            else {
                return "Vanilla";
            }
        }));
    }
	
	@Override
	public void onDisable() {
        if (api != null) { //the api can be null if we are running on unsupported server software.
            api.shutDown(); //complete all inventory futures - ensures /invgive and /endergive will still work even if the server shuts down.
        }
	}

    public InvseeAPI getApi() {
        return api;
    }

    public OfflinePlayerProvider getOfflinePlayerProvider() {
        return offlinePlayerProvider;
    }

    public boolean tabCompleteOfflinePlayers() {
        return getConfig().getBoolean("tabcomplete-offline-players", true);
    }

    public boolean offlinePlayerSupport() {
        return getConfig().getBoolean("enable-offline-player-support", true);
    }

    public boolean unknownPlayerSupport() {
        return getConfig().getBoolean("enable-unknown-player-support", true);
    }

    public String getTitleForInventory(Target target) {
        return getConfig().getString("titles.inventory", "<player>'s inventory")
            .replace("<player>", target.toString());
    }

    public String getTitleForEnderChest(Target target) {
        return getConfig().getString("titles.enderchest", "<player>'s enderchest")
            .replace("<player>", target.toString());
    }

    public String getInventoryTemplate() {
        return getConfig().getString("templates.inventory",
            "i_00 i_01 i_02 i_03 i_04 i_05 i_06 i_07 i_08\n" +
            "i_09 i_10 i_11 i_12 i_13 i_14 i_15 i_16 i_17\n" +
            "i_18 i_19 i_20 i_21 i_22 i_23 i_24 i_25 i_26\n" +
            "i_27 i_28 i_29 i_30 i_31 i_32 i_33 i_34 i_35\n" +
            "a_b  a_l  a_c  a_h  oh   c    _    _    _   \n" +
            "p_00 p_01 p_02 p_03 p_04 p_05 p_06 p_07 p_08");
    }

    public String getEnderChestTemplate() {
        return getConfig().getString("templates.enderchest",
            "e_00 e_01 e_02 e_03 e_04 e_05 e_06 e_07 e_08\n" +
            "e_09 e_10 e_11 e_12 e_13 e_14 e_15 e_16 e_17\n" +
            "e_18 e_19 e_20 e_21 e_22 e_23 e_24 e_25 e_26\n" +
            "e_27 e_28 e_29 e_30 e_31 e_32 e_33 e_34 e_35\n" +
            "e_36 e_37 e_38 e_39 e_40 e_41 e_42 e_43 e_44\n" +
            "e_45 e_46 e_47 e_48 e_49 e_50 e_51 e_52 e_53");
    }

    public LogOptions getLogOptions() {
        FileConfiguration config = getConfig();
        ConfigurationSection logging = config.getConfigurationSection("logging");
        if (logging == null) {
            return LogOptions.empty();
        } else {
            String granularity = logging.getString("granularity", "LOG_ON_CLOSE");
            LogGranularity logGranularity = LogGranularity.valueOf(granularity);
            List<String> output = logging.getStringList("output");
            EnumSet<LogTarget> logTargets = output.stream()
                    .map(LogTarget::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(LogTarget.class)));
            EnumMap<LogTarget, String> formats = new EnumMap<>(LogTarget.class);
            String formatServerLogFile = logging.getString("format-server-log-file");
            if (formatServerLogFile != null) formats.put(LogTarget.SERVER_LOG_FILE, formatServerLogFile);
            String formatPluginLogFile = logging.getString("format-plugin-log-file");
            if (formatPluginLogFile != null) formats.put(LogTarget.PLUGIN_LOG_FILE, formatPluginLogFile);
            String formatSpectatorLogFile = logging.getString("format-spectator-log-file");
            if (formatSpectatorLogFile != null) formats.put(LogTarget.SPECTATOR_LOG_FILE, formatSpectatorLogFile);
            String formatConsole = logging.getString("format-console");
            if (formatConsole != null) formats.put(LogTarget.CONSOLE, formatConsole);
            return LogOptions.of(logGranularity, logTargets, formats);
        }
    }
}
