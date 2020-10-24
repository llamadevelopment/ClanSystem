package net.lldv.clansystem.components.provider;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import net.lldv.clansystem.ClanSystem;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MySqlProvider extends Provider {

    private Connection connection;

    @Override
    public void connect(ClanSystem instance) {
        CompletableFuture.runAsync(() -> {
            try {
                Config config = instance.getConfig();
                Class.forName("com.mysql.jdbc.Driver");
                this.connection = DriverManager.getConnection("jdbc:mysql://" + config.getString("MySql.Host") + ":" + config.getString("MySql.Port") + "/" + config.getString("MySql.Database") + "?autoReconnect=true&useGmtMillisForDatetimes=true&serverTimezone=GMT", config.getString("MySql.User"), config.getString("MySql.Password"));
                // create tables
                instance.getLogger().info("[MySqlClient] Connection opened.");
            } catch (Exception e) {
                e.printStackTrace();
                instance.getLogger().info("[MySqlClient] Failed to connect to database.");
            }
        });
    }

    public void update(String query) {
        CompletableFuture.runAsync(() -> {
            if (this.connection != null) {
                try {
                    PreparedStatement preparedStatement = this.connection.prepareStatement(query);
                    preparedStatement.executeUpdate();
                    preparedStatement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void disconnect(ClanSystem instance) {
        if (this.connection != null) {
            try {
                this.connection.close();
                instance.getLogger().info("[MySqlClient] Connection closed.");
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                instance.getLogger().info("[MySqlClient] Failed to close connection.");
            }
        }
    }

    @Override
    public void createUserData(Player player) {
        super.createUserData(player);
    }

    @Override
    public boolean userExists(String player) {
        return super.userExists(player);
    }

    @Override
    public boolean playerIsInClan(String player) {
        return super.playerIsInClan(player);
    }

    @Override
    public boolean clanNameExists(String name) {
        return super.clanNameExists(name);
    }

    @Override
    public boolean clanTagExists(String tag) {
        return super.clanTagExists(tag);
    }

    @Override
    public void createClan(String name, String tag, Player leader, String inviteSettings) {
        super.createClan(name, tag, leader, inviteSettings);
    }

    @Override
    public void deleteClan(String id) {
        super.deleteClan(id);
    }

    @Override
    public void getClan(String id, Consumer<Clan> clan) {
        super.getClan(id, clan);
    }

    @Override
    public void getClanByTag(String tag, Consumer<Clan> clan) {
        super.getClanByTag(tag, clan);
    }

    @Override
    public void createUserClanRequest(String player, String id) {
        super.createUserClanRequest(player, id);
    }

    @Override
    public void createJoinClanRequest(String player, String id) {
        super.createJoinClanRequest(player, id);
    }

    @Override
    public void deleteClanRequest(String id, String requester) {
        super.deleteClanRequest(id, requester);
    }

    @Override
    public void deleteUserClanRequest(String player, String id) {
        super.deleteUserClanRequest(player, id);
    }

    @Override
    public void getPlayer(String player, Consumer<ClanPlayer> clanPlayer) {
        super.getPlayer(player, clanPlayer);
    }

    @Override
    public void leaveClan(ClanPlayer clanPlayer) {
        super.leaveClan(clanPlayer);
    }

    @Override
    public void joinClan(String player, Clan clan) {
        super.joinClan(player, clan);
    }

    @Override
    public void changeClanRank(ClanPlayer clanPlayer, Clan.ClanRole rank) {
        super.changeClanRank(clanPlayer, rank);
    }

    @Override
    public void renameClan(String id, String name, String tag) {
        super.renameClan(id, name, tag);
    }

    @Override
    public void changeInviteSettings(String id, Clan.InviteSettings inviteSettings) {
        super.changeInviteSettings(id, inviteSettings);
    }

    @Override
    public String getProvider() {
        return super.getProvider();
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
