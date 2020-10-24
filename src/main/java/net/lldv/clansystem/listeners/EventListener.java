package net.lldv.clansystem.listeners;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import net.lldv.clansystem.ClanSystem;

public class EventListener implements Listener {

    private final ClanSystem instance;

    public EventListener(ClanSystem instance) {
        this.instance = instance;
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        this.instance.provider.createUserData(event.getPlayer());
    }

    // FIXME: Only testing, not for release
    @EventHandler
    public void on(PlayerChatEvent event) {
        if (this.instance.isUsePlaceholder()) {
            event.setMessage(this.instance.getPlaceholderAPI().translateString(event.getMessage(), event.getPlayer()));
        }
    }

}
