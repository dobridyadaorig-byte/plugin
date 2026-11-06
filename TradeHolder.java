package dev.simpletrade;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Маркер, по которому мы узнаём окно обмена. */
public final class TradeHolder implements InventoryHolder {

    private final Inventory inventory;

    public TradeHolder(Component title) {
        this.inventory = Bukkit.createInventory(this, TradeSession.SIZE, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
