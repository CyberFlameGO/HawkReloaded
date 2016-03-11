package uk.co.oliwali.HawkEye;

import com.dthielke.herochat.Herochat;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.oliwali.HawkEye.WorldEdit.WESessionFactory;
import uk.co.oliwali.HawkEye.database.DataManager;
import uk.co.oliwali.HawkEye.listeners.*;
import uk.co.oliwali.HawkEye.util.Config;
import uk.co.oliwali.HawkEye.util.Util;

import java.io.IOException;
import java.util.HashMap;

public class HawkEye extends JavaPlugin {

	public String name;
	public String version;
	public Config config;
	public static Server server;
	public static HawkEye instance;
	public MonitorBlockListener monitorBlockListener = new MonitorBlockListener(this);
	public MonitorEntityListener monitorEntityListener = new MonitorEntityListener(this);
	public MonitorPlayerListener monitorPlayerListener = new MonitorPlayerListener(this);
	public MonitorWorldListener monitorWorldListener = new MonitorWorldListener(this);
	public MonitorFallingBlockListener monitorFBListerner = new MonitorFallingBlockListener(this);
	public MonitorWorldEditListener monitorWorldEditListener = new MonitorWorldEditListener();
	public MonitorLiquidFlow monitorLiquidFlow;
	public ToolListener toolListener = new ToolListener();
	private DataManager dbmanager;
	public MonitorHeroChatListener monitorHeroChatListener = new MonitorHeroChatListener(this);

	public static HashMap<String, HashMap<String,Integer>> InvSession = new HashMap<String, HashMap<String,Integer>>();
	public static WorldEditPlugin worldEdit = null;
	public static Herochat herochat = null;

	/**
	 * Safely shuts down HawkEye
	 */
	@Override
	public void onDisable() {

		if (dbmanager != null) {

			try {
				dbmanager.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		Util.info("Version " + version + " disabled!");
	}

	/**
	 * Starts up HawkEye initiation process
	 */
	@Override
	public void onEnable() {
		//Setup metrics
		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			e.printStackTrace();
		}

		PluginManager pm = getServer().getPluginManager();

		//Check bukkit dependencies
		try {
			Class.forName("org.bukkit.event.hanging.HangingPlaceEvent");
		} catch (ClassNotFoundException ex) {
			Util.info("HawkEye requires CraftBukkit 1.4+ to run properly!");
			pm.disablePlugin(this);
			return;
		}

		instance = this;
		server = getServer();
		name = this.getDescription().getName();
		version = this.getDescription().getVersion();

		Util.info("Starting HawkEye " + version + " initiation process...");

		//Load config
		config = new Config(this);

		setupUpdater();

		new SessionManager();

		//Initiate database connection
		try {
			this.dbmanager = new DataManager(this);
			getServer().getScheduler().runTaskTimerAsynchronously(this, dbmanager, Config.LogDelay * 20, Config.LogDelay * 20);
		} catch (Exception e) {
			Util.severe("Error initiating HawkEye database connection, disabling plugin");
			pm.disablePlugin(this);
			return;
		}

		checkDependencies(pm);

		//This must be created while the plugin is loading as the constructor is dependent
        monitorLiquidFlow = new MonitorLiquidFlow(this);
        
		registerListeners(pm);

		getCommand("hawk").setExecutor(new HawkCommand());

		Util.info("Version " + version + " enabled!");
	}

	/**
	 * Checks if required plugins are loaded
	 * @param pm PluginManager
	 */
	private void checkDependencies(PluginManager pm) {
		Plugin we = pm.getPlugin("WorldEdit");
		Plugin hc = pm.getPlugin("Herochat");
		if (we != null) worldEdit = (WorldEditPlugin)we;
		if (hc != null) herochat = (Herochat)hc;
	}

	/**
	 * Registers event listeners
	 * @param pm PluginManager
	 */
	public void registerListeners(PluginManager pm) {
		//Register events
		monitorBlockListener.registerEvents();
		monitorPlayerListener.registerEvents();
		monitorEntityListener.registerEvents();
		monitorWorldListener.registerEvents();
		monitorFBListerner.registerEvents();
		monitorLiquidFlow.registerEvents();
		monitorLiquidFlow.startCacheCleaner();
		pm.registerEvents(toolListener, this);
		if (herochat != null) monitorHeroChatListener.registerEvents();

		if (worldEdit != null)  {
			if (DataType.SUPER_PICKAXE.isLogged()) pm.registerEvents(monitorWorldEditListener, this); //Yes we still need to log superpick!
			
			//This makes sure we OVERRIDE any other plugin that tried to register a EditSessionFactory!
			if (DataType.WORLDEDIT_BREAK.isLogged() || DataType.WORLDEDIT_PLACE.isLogged()) {
				Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
					@Override
					public void run() {
						try {
							Class.forName("com.sk89q.worldedit.extent.logging.AbstractLoggingExtent");
							new WESessionFactory();
						} catch (ClassNotFoundException ex) {
							Util.warning("[!] Failed to initialize WorldEdit logging [!]");
							Util.warning("[!] Please upgrade WorldEdit to 6.0+       [!]");
						}
					}
				}, 2L);
			}
		}
	}

	private void setupUpdater() {
		if (getConfig().getBoolean("general.check-for-updates")) 
			new Updater(this, "hawkeye-reload", this.getFile(), Updater.UpdateType.DEFAULT, false);
	}
}
