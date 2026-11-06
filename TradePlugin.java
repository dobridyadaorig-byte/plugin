package dev.simpletrade;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradePlugin extends JavaPlugin {

    private TradeManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new TradeManager(this);
        getServer().getPluginManager().registerEvents(new TradeListener(this, manager), this);

        PluginCommand cmd = getCommand("trade");
        if (cmd != null) {
            TradeCommand executor = new TradeCommand(manager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.cancelAll("<red>Обмен отменён: сервер перезагружается.");
        }
    }
}
