package net.lldv.clansystem.components.provider;

import cn.nukkit.Player;
import net.lldv.clansystem.ClanSystem;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;

import java.util.function.Consumer;

public class Provider {

    public void connect(ClanSystem instance) {

    }

    public void disconnect(ClanSystem instance) {

    }

    public void createUserData(Player player) {

    }

    public boolean userExists(String player) {
        return false;
    }

    public boolean playerIsInClan(String player) {
        return false;
    }

    public boolean clanNameExists(String name) {
        return false;
    }

    public boolean clanTagExists(String tag) {
        return false;
    }

    public void createClan(String name, String tag, Player leader, String inviteSettings) {

    }

    public void deleteClan(String id) {

    }

    public void getClan(String id, Consumer<Clan> clan) {

    }

    public void getClanByTag(String tag, Consumer<Clan> clan) {
    }

    public void createUserClanRequest(String player, String id) {

    }

    public void createJoinClanRequest(String player, String id) {

    }

    public void deleteClanRequest(String id, String requester) {

    }

    public void deleteUserClanRequest(String player, String id) {

    }

    public void getPlayer(String player, Consumer<ClanPlayer> clanPlayer) {

    }

    public void leaveClan(ClanPlayer clanPlayer) {
    }

    public void joinClan(String player, Clan clan) {

    }

    public void changeClanRank(ClanPlayer clanPlayer, Clan.ClanRole rank) {

    }

    public void renameClan(String id, String name, String tag) {

    }

    public void changeInviteSettings(String id, Clan.InviteSettings inviteSettings) {

    }

    public String getProvider() {
        return null;
    }

}
