package dev.simpletrade;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class TradeCommand implements TabExecutor {

    private final TradeManager manager;

    public TradeCommand(TradeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Эту команду можно использовать только в игре.");
            return true;
        }
        if (args.length != 1) {
            Msg.send(p, "<yellow>Использование: <white>/trade <ник></white>, <white>/trade accept</white>, <white>/trade deny</white>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> manager.accept(p);
            case "deny" -> manager.deny(p);
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Msg.send(p, "<red>Игрок <white>" + args[0] + "<red> не найден онлайн.");
                } else {
                    manager.request(p, target);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String s : new String[]{"accept", "deny"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.equals(sender)) continue;
                if (pl.getName().toLowerCase().startsWith(prefix)) out.add(pl.getName());
            }
        }
        return out;
    }
}
