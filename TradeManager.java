package dev.simpletrade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {

    private record Request(UUID from, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final TradePlugin plugin;
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    /** Ключ — кому адресован запрос. */
    private final Map<UUID, Request> requests = new HashMap<>();

    public TradeManager(TradePlugin plugin) {
        this.plugin = plugin;
    }

    public TradeSession getSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    void remove(TradeSession session) {
        sessions.values().removeIf(s -> s == session);
    }

    /** /trade <ник> */
    public void request(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            Msg.send(from, "<red>Нельзя обмениваться с самим собой.");
            return;
        }
        if (!checkCanTrade(from, to)) return;

        // Если партнёр уже отправлял запрос нам — сразу начинаем обмен
        Request incoming = requests.get(from.getUniqueId());
        if (incoming != null && !incoming.expired() && incoming.from().equals(to.getUniqueId())) {
            requests.remove(from.getUniqueId());
            start(to, from);
            return;
        }

        int timeout = plugin.getConfig().getInt("request-timeout", 30);
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), System.currentTimeMillis() + timeout * 1000L));

        Msg.send(from, "<green>Запрос на обмен отправлен игроку <white>" + to.getName() + "<green>.");
        Msg.send(to, "<white>" + from.getName() + "<yellow> предлагает вам обмен "
                + "<click:run_command:'/trade accept'><green><bold>[Принять]</bold></green></click> "
                + "<click:run_command:'/trade deny'><red><bold>[Отклонить]</bold></red></click>"
                + " <gray>(действует " + timeout + " с.)");
    }

    /** /trade accept */
    public void accept(Player me) {
        Request r = requests.remove(me.getUniqueId());
        if (r == null || r.expired()) {
            Msg.send(me, "<red>У вас нет активных запросов на обмен.");
            return;
        }
        Player sender = Bukkit.getPlayer(r.from());
        if (sender == null) {
            Msg.send(me, "<red>Игрок, отправивший запрос, уже вышел.");
            return;
        }
        if (!checkCanTrade(me, sender)) return;
        start(sender, me);
    }

    /** /trade deny */
    public void deny(Player me) {
        Request r = requests.remove(me.getUniqueId());
        if (r == null || r.expired()) {
            Msg.send(me, "<red>У вас нет активных запросов на обмен.");
            return;
        }
        Msg.send(me, "<yellow>Вы отклонили запрос на обмен.");
        Player sender = Bukkit.getPlayer(r.from());
        if (sender != null) {
            Msg.send(sender, "<red>" + me.getName() + " отклонил ваш запрос на обмен.");
        }
    }

    private boolean checkCanTrade(Player from, Player to) {
        if (sessions.containsKey(from.getUniqueId())) {
            Msg.send(from, "<red>Вы уже находитесь в обмене.");
            return false;
        }
        if (sessions.containsKey(to.getUniqueId())) {
            Msg.send(from, "<red>" + to.getName() + " сейчас обменивается с кем-то другим.");
            return false;
        }
        double max = plugin.getConfig().getDouble("max-distance", 0);
        if (max > 0) {
            boolean near = from.getWorld().equals(to.getWorld())
                    && from.getLocation().distanceSquared(to.getLocation()) <= max * max;
            if (!near) {
                Msg.send(from, "<red>Вы должны быть не дальше " + (int) max + " блоков от игрока.");
                return false;
            }
        }
        return true;
    }

    private void start(Player p1, Player p2) {
        TradeSession session = new TradeSession(plugin, this, p1, p2);
        sessions.put(p1.getUniqueId(), session);
        sessions.put(p2.getUniqueId(), session);
        session.start();
    }

    /** Игрок вышел с сервера. */
    public void handleQuit(Player p) {
        requests.remove(p.getUniqueId());
        requests.values().removeIf(r -> r.from().equals(p.getUniqueId()));
        TradeSession s = getSession(p);
        if (s != null) {
            s.cancel(p, "<red>Обмен отменён: " + p.getName() + " вышел с сервера.");
        }
    }

    public void cancelAll(String reason) {
        for (TradeSession s : new ArrayList<>(new java.util.HashSet<>(sessions.values()))) {
            s.cancel(null, reason);
        }
        requests.clear();
    }
}
