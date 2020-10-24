package net.lldv.clansystem.commands;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.PluginCommand;
import net.lldv.clansystem.ClanSystem;

public class ClanCommand extends PluginCommand<ClanSystem> {

    public ClanCommand(ClanSystem owner) {
        super(owner.getConfig().getString("Commands.Clan.Name"), owner);
        this.setDescription(owner.getConfig().getString("Commands.Clan.Description"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            this.getPlugin().getFormWindows().openClanMenu(player);
        }
        return true;
    }

}
