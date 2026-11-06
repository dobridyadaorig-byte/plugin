package dev.simpletrade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class TradeListener implements Listener {

    private final TradePlugin plugin;
    private final TradeManager manager;

    public TradeListener(TradePlugin plugin, TradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeHolder)) return;

        TradeSession session = manager.getSession(p);
        if (session == null) {
            e.setCancelled(true);
            return;
        }

        // Эти действия могут вытянуть предметы партнёра или служебные кнопки
        if (e.getClick() == ClickType.DOUBLE_CLICK
                || e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || e.getAction() == InventoryAction.CLONE_STACK) {
            e.setCancelled(true);
            return;
        }

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) {
            return; // клик вне окна (выброс предмета с курсора) — разрешаем
        }

        if (clicked.equals(top)) {
            int slot = e.getSlot();

            if (slot == TradeSession.READY_SLOT) {
                e.setCancelled(true);
                session.toggleReady(p);
                return;
            }
            if (slot == TradeSession.CANCEL_SLOT) {
                e.setCancelled(true);
                session.cancel(null, "<red>Обмен отменён: " + p.getName() + " нажал «Отменить».");
                return;
            }
            if (!TradeSession.isOwnSlot(slot)) {
                e.setCancelled(true);
                return;
            }
            scheduleChange(session);
        } else {
            // Нижний инвентарь игрока
            if (e.isShiftClick()) {
                // Шифт-клик по умолчанию кинул бы предмет в любой свободный слот окна, делаем сами
                e.setCancelled(true);
                ItemStack current = e.getCurrentItem();
                if (current == null || current.getType().isAir()) return;
                ItemStack rest = TradeSession.addToOffer(top, current);
                e.setCurrentItem(rest);
                scheduleChange(session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeHolder)) return;

        TradeSession session = manager.getSession(p);
        if (session == null) {
            e.setCancelled(true);
            return;
        }

        boolean touchesTop = false;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                touchesTop = true;
                if (!TradeSession.isOwnSlot(raw)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        if (touchesTop) {
            scheduleChange(session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof TradeHolder)) return;

        TradeSession session = manager.getSession(p);
        if (session != null) {
            session.cancel(p, "<red>Обмен отменён: " + p.getName() + " закрыл окно обмена.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.handleQuit(e.getPlayer());
    }

    /** Изменение предложения применяем в следующем тике, когда инвентарь уже обновился. */
    private void scheduleChange(TradeSession session) {
        Bukkit.getScheduler().runTask(plugin, session::offerChanged);
    }
}
