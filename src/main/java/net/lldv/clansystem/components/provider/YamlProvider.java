package net.lldv.clansystem.components.provider;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import net.lldv.clansystem.ClanSystem;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class YamlProvider extends Provider {

    private Config clanData;
    private Config playerData;

    @Override
    public void connect(ClanSystem instance) {
        instance.saveResource("/data/clan_data.yml");
        instance.saveResource("/data/player_data.yml");
        this.clanData = new Config(instance.getDataFolder() + "/data/clan_data.yml", Config.YAML);
        this.playerData = new Config(instance.getDataFolder() + "/data/player_data.yml", Config.YAML);
        instance.getLogger().info("[Configuration] Ready.");
    }

    @Override
    public void createUserData(Player player) {
        if (!this.userExists(player.getName())) {
            List<String> list = new ArrayList<>();
            this.playerData.set("player." + player.getName() + ".requests", list);
            this.playerData.set("player." + player.getName() + ".clan", "null");
            this.playerData.set("player." + player.getName() + ".role", "MEMBER");
            this.playerData.save();
            this.playerData.reload();
        }

        if (this.playerIsInClan(player.getName())) {
            CompletableFuture.runAsync(() -> this.getPlayer(player.getName(), clanPlayer -> this.getClan(clanPlayer.getClan(), clan -> ClanSystem.getInstance().getCachedClanPlayers().put(player.getName(), clan))));
        }
    }

    @Override
    public boolean userExists(String player) {
        return this.playerData.exists("player." + player + ".clan");
    }

    @Override
    public boolean playerIsInClan(String player) {
        String s = this.playerData.getString("player." + player + ".clan");
        return !s.equals("null");
    }

    @Override
    public boolean clanNameExists(String name) {
        boolean value = false;
        for (String s : this.clanData.getSection("clan").getAll().getKeys(false)) {
            if (this.clanData.getString("clan." + s + ".name").equals(name)) value = true;
        }
        return value;
    }

    @Override
    public boolean clanTagExists(String tag) {
        boolean value = false;
        for (String s : this.clanData.getSection("clan").getAll().getKeys(false)) {
            if (this.clanData.getString("clan." + s + ".tag").equals(tag)) value = true;
        }
        return value;
    }

    @Override
    public void createClan(String name, String tag, Player leader, String inviteSettings) {
        String id = this.getRandomId();
        List<String> members = new ArrayList<>();
        List<String> requests = new ArrayList<>();
        members.add(leader.getName() + ":LEADER");
        this.clanData.set("clan." + id + ".name", name);
        this.clanData.set("clan." + id + ".tag", tag);
        this.clanData.set("clan." + id + ".members", members);
        this.clanData.set("clan." + id + ".requests", requests);
        this.clanData.set("clan." + id + ".invite", inviteSettings);
        this.clanData.save();
        this.clanData.reload();

        this.playerData.set("player." + leader.getName() + ".clan", id);
        this.playerData.set("player." + leader.getName() + ".role", "LEADER");
        this.playerData.save();
        this.playerData.reload();

        ClanSystem.getInstance().getCachedClanPlayers().put(leader.getName(), new Clan(name, tag, id, null, null, null));
    }

    @Override
    public void deleteClan(String id) {
        this.getClan(id, clan -> {
            clan.getMembers().forEach(clanPlayer -> {
                this.playerData.set("player." + clanPlayer.getPlayer() + ".clan", "null");
                this.playerData.set("player." + clanPlayer.getPlayer() + ".role", "MEMBER");
                this.playerData.save();
                this.playerData.reload();
                ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
            });
            Map<String, Object> map = this.clanData.getSection("clan").getAllMap();
            map.remove(id);
            this.clanData.set("clan", map);
            this.clanData.save();
            this.clanData.reload();
        });
    }

    @Override
    public void getClan(String id, Consumer<Clan> clan) {
        String name = this.clanData.getString("clan." + id + ".name");
        String tag = this.clanData.getString("clan." + id + ".tag");
        List<String> requests = this.clanData.getStringList("clan." + id + ".requests");
        Clan.InviteSettings inviteSettings = Clan.InviteSettings.valueOf(this.clanData.getString("clan." + id + ".invite"));
        List<ClanPlayer> clanPlayers = new ArrayList<>();
        for (String s : this.clanData.getStringList("clan." + id + ".members")) {
            String[] data = s.split(":");
            this.getPlayer(data[0], e -> clanPlayers.add(new ClanPlayer(data[0], e.getRequests(), id, data[1])));
        }
        clan.accept(new Clan(name, tag, id, inviteSettings, clanPlayers, requests));
    }

    @Override
    public void getClanByTag(String tag, Consumer<Clan> clan) {
        for (String s : this.clanData.getSection("clan").getAll().getKeys(false)) {
            if (this.clanData.getString("clan." + s + ".tag").equals(tag)) {
                this.getClan(s, rawClan -> {
                    clan.accept(rawClan);
                });
            }
        }
    }

    @Override
    public void createUserClanRequest(String player, String id) {
        List<String> list = this.playerData.getStringList("player." + player + ".requests");
        list.add(id);
        this.playerData.set("player." + player + ".requests", list);
        this.playerData.save();
        this.playerData.reload();
    }

    @Override
    public void createJoinClanRequest(String player, String id) {
        List<String> list = this.clanData.getStringList("clan." + id + ".requests");
        list.add(player);
        this.clanData.set("clan." + id + ".requests", list);
        this.clanData.save();
        this.clanData.reload();
    }

    @Override
    public void deleteClanRequest(String id, String requester) {
        List<String> list = this.clanData.getStringList("clan." + id + ".requests");
        list.remove(requester);
        this.clanData.set("clan." + id + ".requests", list);
        this.clanData.save();
        this.clanData.reload();
    }

    @Override
    public void deleteUserClanRequest(String player, String id) {
        List<String> list = this.playerData.getStringList("player." + player + ".requests");
        list.remove(id);
        this.playerData.set("player." + player + ".requests", list);
        this.playerData.save();
        this.playerData.reload();
    }

    @Override
    public void getPlayer(String player, Consumer<ClanPlayer> clanPlayer) {
        List<String> requests = this.playerData.getStringList("player." + player + ".requests");
        String clan = this.playerData.getString("player." + player + ".clan");
        String role = this.playerData.getString("player." + player + ".role");
        clanPlayer.accept(new ClanPlayer(player, requests, clan, role));
    }

    @Override
    public void leaveClan(ClanPlayer clanPlayer) {
        this.playerData.set("player." + clanPlayer.getPlayer() + ".clan", "null");
        this.playerData.set("player." + clanPlayer.getPlayer() + ".role", "MEMBER");
        this.playerData.save();
        this.playerData.reload();

        List<String> list = this.clanData.getStringList("clan." + clanPlayer.getClan() + ".members");
        list.remove(clanPlayer.getPlayer() + ":" + clanPlayer.getRole());
        this.clanData.set("clan." + clanPlayer.getClan() + ".members", list);
        this.clanData.save();
        this.clanData.reload();

        ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
    }

    @Override
    public void joinClan(String player, Clan clan) {
        this.playerData.set("player." + player + ".clan", clan.getId());
        this.playerData.set("player." + player + ".role", "MEMBER");
        List<String> requests = this.playerData.getStringList("player." + player + ".requests");
        requests.clear();
        this.playerData.set("player." + player + ".requests", requests);
        this.playerData.save();
        this.playerData.reload();

        List<String> members = this.clanData.getStringList("clan." + clan.getId() + ".members");
        members.add(player + ":" + "MEMBER");
        this.clanData.set("clan." + clan.getId() + ".members", members);
        this.clanData.save();
        this.clanData.reload();

        ClanSystem.getInstance().getCachedClanPlayers().put(player, clan);
    }

    @Override
    public void changeClanRank(ClanPlayer clanPlayer, Clan.ClanRole rank) {
        this.playerData.set("player." + clanPlayer.getPlayer() + ".role", rank.name().toUpperCase());
        this.playerData.save();
        this.playerData.reload();

        List<String> list = this.clanData.getStringList("clan." + clanPlayer.getClan() + ".members");
        list.remove(clanPlayer.getPlayer() + ":" + clanPlayer.getRole());
        list.add(clanPlayer.getPlayer() + ":" + rank.name().toUpperCase());
        this.clanData.set("clan." + clanPlayer.getClan() + ".members", list);
        this.clanData.save();
        this.clanData.reload();
    }

    @Override
    public void renameClan(String id, String name, String tag) {
        this.clanData.set("clan." + id + ".name", name);
        this.clanData.set("clan." + id + ".tag", tag);
        this.clanData.save();
        this.clanData.reload();
    }

    @Override
    public void changeInviteSettings(String id, Clan.InviteSettings inviteSettings) {
        this.clanData.set("clan." + id + ".invite", inviteSettings.name().toUpperCase());
        this.clanData.save();
        this.clanData.reload();
    }

    @Override
    public String getProvider() {
        return "Yaml";
    }

    public String getRandomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder stringBuilder = new StringBuilder();
        Random rnd = new Random();
        while (stringBuilder.length() < 15) {
            int index = (int) (rnd.nextFloat() * chars.length());
            stringBuilder.append(chars.charAt(index));
        }
        return stringBuilder.toString();
    }

}
