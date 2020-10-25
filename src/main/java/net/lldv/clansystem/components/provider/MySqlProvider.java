package net.lldv.clansystem.components.provider;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import net.lldv.clansystem.ClanSystem;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                this.update("CREATE TABLE IF NOT EXISTS player_data(player VARCHAR(30), requests LONGTEXT, clan VARCHAR(15), role VARCHAR(10), PRIMARY KEY (player));");
                this.update("CREATE TABLE IF NOT EXISTS clan_data(id VARCHAR(15), name VARCHAR(100), tag VARCHAR(5), members LONGTEXT, requests LONGTEXT, invite VARCHAR(10), PRIMARY KEY (id));");
                instance.getLogger().info("[MySqlClient] Connection opened.");
            } catch (Exception e) {
                e.printStackTrace();
                instance.getLogger().info("[MySqlClient] Failed to connect to database.");
            }
        });
    }

    public void update(String query) {
        try {
            if (this.connection != null) {
                PreparedStatement preparedStatement = this.connection.prepareStatement(query);
                preparedStatement.executeUpdate();
                preparedStatement.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (!this.userExists(player.getName())) {
            CompletableFuture.runAsync(() -> this.update("INSERT INTO player_data (PLAYER, REQUESTS, CLAN, ROLE) VALUES ('" + player.getName() + "', '', 'null', 'MEMBER');"));
        }

        if (this.playerIsInClan(player.getName())) {
            CompletableFuture.runAsync(() -> this.getPlayer(player.getName(), clanPlayer -> this.getClan(clanPlayer.getClan(), clan -> ClanSystem.getInstance().getCachedClanPlayers().put(player.getName(), clan))));
        }
    }

    @Override
    public boolean userExists(String player) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM player_data WHERE PLAYER = ?");
            preparedStatement.setString(1, player);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) return resultSet.getString("PLAYER") != null;
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean playerIsInClan(String player) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM player_data WHERE PLAYER = ?");
            preparedStatement.setString(1, player);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) return !resultSet.getString("CLAN").equals("null");
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean clanNameExists(String name) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) if (resultSet.getString("NAME").equals(name)) return true;
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean clanTagExists(String tag) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) if (resultSet.getString("TAG").equals(tag)) return true;
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void createClan(String name, String tag, Player leader, String inviteSettings) {
        CompletableFuture.runAsync(() -> {
            String id = this.getRandomId();
            this.update("INSERT INTO clan_data (ID, NAME, TAG, MEMBERS, REQUESTS, INVITE) VALUES ('" + id + "', '" + name + "', '" + tag + "', '" + leader.getName() + ":LEADER#', '', '" + inviteSettings + "');");
            this.update("UPDATE player_data SET CLAN= '" + id + "' WHERE PLAYER= '" + leader.getName() + "';");
            this.update("UPDATE player_data SET ROLE= '" + "LEADER" + "' WHERE PLAYER= '" + leader.getName() + "';");

            ClanSystem.getInstance().getCachedClanPlayers().put(leader.getName(), new Clan(name, tag, id, null, null, null));
        });
    }

    @Override
    public void deleteClan(String id) {
        CompletableFuture.runAsync(() -> this.getClan(id, clan -> {
            clan.getMembers().forEach(clanPlayer -> {
                this.update("UPDATE player_data SET CLAN= '" + "null" + "' WHERE PLAYER= '" + clanPlayer.getPlayer() + "';");
                this.update("UPDATE player_data SET ROLE= '" + "MEMBER" + "' WHERE PLAYER= '" + clanPlayer.getPlayer() + "';");
                ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
            });
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("DELETE FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, id);
                preparedStatement.executeUpdate();
                preparedStatement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }));
    }

    @Override
    public void getClan(String id, Consumer<Clan> clan) {
        if (id.isEmpty()) return;
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
            preparedStatement.setString(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String name = resultSet.getString("NAME");
                String tag = resultSet.getString("TAG");
                String[] rawRequests = resultSet.getString("REQUESTS").split(":");
                List<String> requests = new ArrayList<>();
                for (String s : rawRequests) {
                    if (!s.isEmpty()) requests.add(s);
                }
                Clan.InviteSettings inviteSettings = Clan.InviteSettings.valueOf(resultSet.getString("INVITE"));
                List<ClanPlayer> clanPlayers = new ArrayList<>();
                for (String s : resultSet.getString("MEMBERS").split("#")) {
                    String[] data = s.split(":");
                    this.getPlayer(data[0], e -> clanPlayers.add(new ClanPlayer(data[0], e.getRequests(), id, data[1])));
                }
                clan.accept(new Clan(name, tag, id, inviteSettings, clanPlayers, requests));
            }
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getClanByTag(String tag, Consumer<Clan> clan) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE TAG = ?");
            preparedStatement.setString(1, tag);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                this.getClan(resultSet.getString("ID"), rawClan -> {
                    clan.accept(rawClan);
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createUserClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM player_data WHERE PLAYER = ?");
                preparedStatement.setString(1, player);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String set = resultSet.getString("REQUESTS");
                    set = set + ":" + id;
                    this.update("UPDATE player_data SET REQUESTS= '" + set + "' WHERE PLAYER= '" + player + "';");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void createJoinClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, id);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String set = resultSet.getString("REQUESTS");
                    set = set + ":" + player;
                    this.update("UPDATE clan_data SET REQUESTS= '" + set + "' WHERE ID= '" + id + "';");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void deleteClanRequest(String id, String requester) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, id);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String[] set = resultSet.getString("REQUESTS").split(":");
                    StringBuilder setSet = new StringBuilder();
                    for (String s : set) {
                        if (!s.equals(requester)) {
                            setSet.append(s).append(":");
                        }
                    }
                    this.update("UPDATE clan_data SET REQUESTS= '" + setSet.toString() + "' WHERE ID= '" + id + "';");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void deleteUserClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM player_data WHERE PLAYER = ?");
                preparedStatement.setString(1, player);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String[] set = resultSet.getString("REQUESTS").split(":");
                    StringBuilder setSet = new StringBuilder();
                    for (String s : set) {
                        if (!s.equals(id)) {
                            setSet.append(s).append(":");
                        }
                    }
                    this.update("UPDATE player_data SET REQUESTS= '" + setSet.toString() + "' WHERE PLAYER= '" + player + "';");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void getPlayer(String player, Consumer<ClanPlayer> clanPlayer) {
        try {
            PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM player_data WHERE PLAYER = ?");
            preparedStatement.setString(1, player);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String clan = resultSet.getString("CLAN");
                String role = resultSet.getString("ROLE");
                String[] requests = resultSet.getString("REQUESTS").split(":");
                clanPlayer.accept(new ClanPlayer(player, Arrays.asList(requests), clan, role));
            }
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void leaveClan(ClanPlayer clanPlayer) {
        CompletableFuture.runAsync(() -> {
            this.update("UPDATE player_data SET CLAN= '" + "null" + "' WHERE PLAYER= '" + clanPlayer.getPlayer() + "';");
            this.update("UPDATE player_data SET ROLE= '" + "MEMBER" + "' WHERE PLAYER= '" + clanPlayer.getPlayer() + "';");
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, clanPlayer.getClan());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String[] set = resultSet.getString("MEMBERS").split("#");
                    StringBuilder setSet = new StringBuilder();
                    for (String s : set) {
                        if (!s.equals(clanPlayer.getPlayer() + ":" + clanPlayer.getRole())) {
                            setSet.append(s).append("#");
                        }
                    }
                    this.update("UPDATE clan_data SET MEMBERS= '" + setSet.toString() + "' WHERE ID= '" + clanPlayer.getClan() + "';");
                }
                ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void joinClan(String player, Clan clan) {
        CompletableFuture.runAsync(() -> {
            this.update("UPDATE player_data SET CLAN= '" + clan.getId() + "' WHERE PLAYER= '" + player + "';");
            this.update("UPDATE player_data SET ROLE= '" + "MEMBER" + "' WHERE PLAYER= '" + player + "';");
            this.update("UPDATE player_data SET REQUESTS= '' WHERE PLAYER= '" + player + "';");

            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, clan.getId());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String set = resultSet.getString("MEMBERS");
                    set = set + player + ":" + "MEMBER" + "#";
                    this.update("UPDATE clan_data SET MEMBERS= '" + set + "' WHERE ID= '" + clan.getId() + "';");
                }
                ClanSystem.getInstance().getCachedClanPlayers().put(player, clan);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void changeClanRank(ClanPlayer clanPlayer, Clan.ClanRole rank) {
        CompletableFuture.runAsync(() -> {
            this.update("UPDATE player_data SET ROLE= '" + rank.name().toUpperCase() + "' WHERE PLAYER= '" + clanPlayer.getPlayer() + "';");
            try {
                PreparedStatement preparedStatement = this.connection.prepareStatement("SELECT * FROM clan_data WHERE ID = ?");
                preparedStatement.setString(1, clanPlayer.getClan());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    String[] set = resultSet.getString("MEMBERS").split("#");
                    StringBuilder setSet = new StringBuilder();
                    for (String s : set) {
                        if (s.equals(clanPlayer.getPlayer() + ":" + clanPlayer.getRole())) {
                            setSet.append(clanPlayer.getPlayer()).append(":").append(rank.name().toUpperCase()).append("#");
                        } else setSet.append(s).append("#");
                    }
                    this.update("UPDATE clan_data SET MEMBERS= '" + setSet.toString() + "' WHERE ID= '" + clanPlayer.getClan() + "';");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void renameClan(String id, String name, String tag) {
        CompletableFuture.runAsync(() -> {
            this.update("UPDATE clan_data SET NAME= '" + name + "' WHERE ID= '" + id + "';");
            this.update("UPDATE clan_data SET TAG= '" + tag + "' WHERE ID= '" + id + "';");
        });
    }

    @Override
    public void changeInviteSettings(String id, Clan.InviteSettings inviteSettings) {
        CompletableFuture.runAsync(() -> {
            this.update("UPDATE clan_data SET INVITE= '" + inviteSettings.name().toUpperCase() + "' WHERE ID= '" + id + "';");
        });
    }

    @Override
    public String getProvider() {
        return "MySql";
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
