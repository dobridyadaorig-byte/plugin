package dev.simpletrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

final class Msg {

    static final MiniMessage MM = MiniMessage.miniMessage();
    static final String PREFIX = "<gray>[<gold>Обмен</gold>]</gray> ";

    private Msg() {
    }

    /** Сообщение игроку с префиксом (формат MiniMessage). */
    static void send(Player player, String miniMessage) {
        player.sendMessage(MM.deserialize(PREFIX + miniMessage));
    }

    /** Компонент для названий предметов (без курсива). */
    static Component name(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
