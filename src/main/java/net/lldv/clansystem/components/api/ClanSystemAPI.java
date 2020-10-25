package net.lldv.clansystem.components.api;

import cn.nukkit.Player;
import cn.nukkit.Server;
import lombok.Getter;
import lombok.Setter;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.provider.Provider;

public class ClanSystemAPI {

    @Getter
    @Setter
    public static Provider provider;

    public static void broadcastClanMessage(Clan clan, String message) {
        clan.getMembers().forEach(clanPlayer -> {
            Player player = Server.getInstance().getPlayer(clanPlayer.getPlayer());
            if (player != null) player.sendMessage(message);
        });
    }

    public static void broadcastClanMessage(Clan clan, Clan.ClanRole clanRole, String message) {
        clan.getMembers().forEach(clanPlayer -> {
            if (Clan.ClanRole.valueOf(clanPlayer.getRole()) == clanRole) {
                Player player = Server.getInstance().getPlayer(clanPlayer.getPlayer());
                if (player != null) player.sendMessage(message);
            }
        });
    }

}
