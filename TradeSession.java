package dev.simpletrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Одна сделка между двумя игроками.
 *
 * Раскладка окна (6 рядов x 9):
 *  колонки 0-3 — ваши предметы (можно класть/брать),
 *  колонка 4   — служебные кнопки,
 *  колонки 5-8 — предметы партнёра (только просмотр).
 */
public final class TradeSession {

    public static final int SIZE = 54;
    public static final int PARTNER_STATUS_SLOT = 13;
    public static final int TIMER_SLOT = 31;
    public static final int READY_SLOT = 40;
    public static final int CANCEL_SLOT = 49;

    /** Слот, куда игрок может класть свои предметы. */
    public static boolean isOwnSlot(int slot) {
        return slot >= 0 && slot < SIZE && slot % 9 < 4;
    }

    public static final class Side {
        final Player player;
        final TradeHolder holder;
        boolean ready;

        Side(Player player, TradeHolder holder) {
            this.player = player;
            this.holder = holder;
        }
    }

    private final TradePlugin plugin;
    private final TradeManager manager;
    private final Side a;
    private final Side b;
    private boolean active = true;
    private BukkitTask countdown;
    private int left;

    public TradeSession(TradePlugin plugin, TradeManager manager, Player p1, Player p2) {
        this.plugin = plugin;
        this.manager = manager;
        this.a = new Side(p1, new TradeHolder(Msg.MM.deserialize("<dark_gray>Обмен с <white>" + p2.getName())));
        this.b = new Side(p2, new TradeHolder(Msg.MM.deserialize("<dark_gray>Обмен с <white>" + p1.getName())));
    }

    public Side sideOf(Player p) {
        if (a.player.getUniqueId().equals(p.getUniqueId())) return a;
        if (b.player.getUniqueId().equals(p.getUniqueId())) return b;
        return null;
    }

    public Player playerA() {
        return a.player;
    }

    public Player playerB() {
        return b.player;
    }

    private Side other(Side s) {
        return s == a ? b : a;
    }

    public void start() {
        sync();
        a.player.openInventory(a.holder.getInventory());
        b.player.openInventory(b.holder.getInventory());
    }

    /** Вызывается после любого изменения предложений. Сбрасывает готовность. */
    public void offerChanged() {
        if (!active) return;
        a.ready = false;
        b.ready = false;
        stopCountdown();
        sync();
    }

    public void toggleReady(Player p) {
        if (!active) return;
        Side s = sideOf(p);
        if (s == null) return;
        s.ready = !s.ready;
        stopCountdown();
        if (a.ready && b.ready) {
            startCountdown();
        }
        sync();
    }

    private void startCountdown() {
        left = Math.max(1, plugin.getConfig().getInt("countdown-seconds", 3));
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            left--;
            if (left <= 0) {
                complete();
            } else {
                sync();
            }
        }, 20L, 20L);
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    /** Обновляет оба окна: предметы партнёра и кнопки. */
    private void sync() {
        for (Side s : new Side[]{a, b}) {
            Side o = other(s);
            Inventory inv = s.holder.getInventory();
            Inventory oInv = o.holder.getInventory();

            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 4; col++) {
                    ItemStack theirs = oInv.getItem(row * 9 + col);
                    inv.setItem(row * 9 + 5 + col, theirs == null ? null : theirs.clone());
                }
            }

            for (int row = 0; row < 6; row++) {
                inv.setItem(row * 9 + 4, filler());
            }

            inv.setItem(PARTNER_STATUS_SLOT, o.ready
                    ? button(Material.LIME_STAINED_GLASS_PANE, "<green>" + o.player.getName() + " готов")
                    : button(Material.RED_STAINED_GLASS_PANE, "<red>" + o.player.getName() + " ещё не готов"));

            inv.setItem(READY_SLOT, s.ready
                    ? button(Material.LIME_CONCRETE, "<green>Вы готовы <gray>(нажмите, чтобы отменить)")
                    : button(Material.RED_CONCRETE, "<red>Нажмите, когда будете готовы"));

            inv.setItem(CANCEL_SLOT, button(Material.BARRIER, "<red>Отменить обмен"));

            if (countdown != null) {
                ItemStack clock = button(Material.CLOCK, "<yellow>Обмен через " + left + " с.");
                clock.setAmount(Math.max(1, Math.min(left, 64)));
                inv.setItem(TIMER_SLOT, clock);
            }
        }
    }

    /** Завершает обмен: каждый получает предметы партнёра. */
    public void complete() {
        if (!active) return;
        if (!(a.ready && b.ready)) return;
        if (!a.player.isOnline() || !b.player.isOnline()) {
            cancel(null, "<red>Обмен отменён: игрок вышел.");
            return;
        }
        active = false;
        stopCountdown();
        manager.remove(this);

        List<ItemStack> fromA = takeOffer(a);
        List<ItemStack> fromB = takeOffer(b);

        closeIfOpen(a.player);
        closeIfOpen(b.player);

        give(b.player, fromA);
        give(a.player, fromB);

        Msg.send(a.player, "<green>Обмен с <white>" + b.player.getName() + "<green> успешно завершён!");
        Msg.send(b.player, "<green>Обмен с <white>" + a.player.getName() + "<green> успешно завершён!");
    }

    /**
     * Отменяет обмен, предметы возвращаются владельцам.
     *
     * @param closer игрок, чьё окно уже закрывается (его окно повторно не закрываем), может быть null
     */
    public void cancel(Player closer, String reasonMiniMessage) {
        if (!active) return;
        active = false;
        stopCountdown();
        manager.remove(this);

        List<ItemStack> backA = takeOffer(a);
        List<ItemStack> backB = takeOffer(b);

        if (closer == null || !closer.getUniqueId().equals(a.player.getUniqueId())) closeIfOpen(a.player);
        if (closer == null || !closer.getUniqueId().equals(b.player.getUniqueId())) closeIfOpen(b.player);

        give(a.player, backA);
        give(b.player, backB);

        if (a.player.isOnline()) Msg.send(a.player, reasonMiniMessage);
        if (b.player.isOnline()) Msg.send(b.player, reasonMiniMessage);
    }

    /** Забирает предметы из «своих» слотов и очищает их. */
    private List<ItemStack> takeOffer(Side s) {
        List<ItemStack> items = new ArrayList<>();
        Inventory inv = s.holder.getInventory();
        for (int i = 0; i < SIZE; i++) {
            if (!isOwnSlot(i)) continue;
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                items.add(it.clone());
                inv.setItem(i, null);
            }
        }
        return items;
    }

    private void give(Player p, List<ItemStack> items) {
        boolean dropped = false;
        for (ItemStack it : items) {
            for (ItemStack rest : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItem(p.getLocation(), rest);
                dropped = true;
            }
        }
        if (dropped && p.isOnline()) {
            Msg.send(p, "<yellow>Инвентарь полон — часть предметов выпала под ноги.");
        }
    }

    private void closeIfOpen(Player p) {
        if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof TradeHolder) {
            p.closeInventory();
        }
    }

    /** Кладёт предмет в свободные «свои» слоты. Возвращает остаток или null. */
    public static ItemStack addToOffer(Inventory inv, ItemStack stack) {
        ItemStack rest = stack.clone();

        for (int i = 0; i < SIZE && rest.getAmount() > 0; i++) {
            if (!isOwnSlot(i)) continue;
            ItemStack cur = inv.getItem(i);
            if (cur != null && cur.isSimilar(rest) && cur.getAmount() < cur.getMaxStackSize()) {
                int move = Math.min(cur.getMaxStackSize() - cur.getAmount(), rest.getAmount());
                cur.setAmount(cur.getAmount() + move);
                inv.setItem(i, cur);
                rest.setAmount(rest.getAmount() - move);
            }
        }

        for (int i = 0; i < SIZE && rest.getAmount() > 0; i++) {
            if (!isOwnSlot(i)) continue;
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType().isAir()) {
                int move = Math.min(rest.getMaxStackSize(), rest.getAmount());
                ItemStack put = rest.clone();
                put.setAmount(move);
                inv.setItem(i, put);
                rest.setAmount(rest.getAmount() - move);
            }
        }

        return rest.getAmount() > 0 ? rest : null;
    }

    private static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.name(name));
        item.setItemMeta(meta);
        return item;
    }
}
