package net.lldv.clansystem.components.provider;

import cn.nukkit.Player;
import cn.nukkit.utils.Config;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.lldv.clansystem.ClanSystem;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MongodbProvider extends Provider {

    private final Config config = ClanSystem.getInstance().getConfig();

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> playerDataCollection, clanDataCollection;

    @Override
    public void connect(ClanSystem instance) {
        CompletableFuture.runAsync(() -> {
            MongoClientURI uri = new MongoClientURI(this.config.getString("MongoDB.Uri"));
            this.mongoClient = new MongoClient(uri);
            this.mongoDatabase = this.mongoClient.getDatabase(this.config.getString("MongoDB.Database"));
            this.playerDataCollection = this.mongoDatabase.getCollection("player_data");
            this.clanDataCollection = this.mongoDatabase.getCollection("clan_data");
            Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
            mongoLogger.setLevel(Level.OFF);
            instance.getLogger().info("[MongoClient] Connection opened.");
        });
    }

    @Override
    public void disconnect(ClanSystem instance) {
        this.mongoClient.close();
        instance.getLogger().info("[MongoClient] Connection closed.");
    }

    @Override
    public void createUserData(Player player) {
        if (!this.userExists(player.getName())) {
            List<String> list = new ArrayList<>();
            Document document = new Document("player", player.getName())
                    .append("requests", list)
                    .append("clan", "null")
                    .append("role", "MEMBER");
            this.playerDataCollection.insertOne(document);
        }

        if (this.playerIsInClan(player.getName())) {
            CompletableFuture.runAsync(() -> this.getPlayer(player.getName(), clanPlayer -> this.getClan(clanPlayer.getClan(), clan -> ClanSystem.getInstance().getCachedClanPlayers().put(player.getName(), clan))));
        }
    }

    @Override
    public boolean userExists(String player) {
        return this.playerDataCollection.find(new Document("player", player)).first() != null;
    }

    @Override
    public boolean playerIsInClan(String player) {
        Document document = this.playerDataCollection.find(new Document("player", player)).first();
        assert document != null;
        String s = document.getString("clan");
        return !s.equals("null");
    }

    @Override
    public boolean clanNameExists(String name) {
        return this.clanDataCollection.find(new Document("name", name)).first() != null;
    }

    @Override
    public boolean clanTagExists(String tag) {
        return this.clanDataCollection.find(new Document("tag", tag)).first() != null;
    }

    @Override
    public void createClan(String name, String tag, Player leader, String inviteSettings) {
        CompletableFuture.runAsync(() -> {
            String id = this.getRandomId();
            List<String> members = new ArrayList<>();
            List<String> requests = new ArrayList<>();
            members.add(leader.getName() + ":LEADER");
            Document document = new Document("name", name)
                    .append("id", id)
                    .append("tag", tag)
                    .append("members", members)
                    .append("requests", requests)
                    .append("invite", inviteSettings);
            this.clanDataCollection.insertOne(document);

            Bson clanEntry = new Document("$set", new Document("clan", id));
            this.playerDataCollection.updateMany(new Document("player", leader.getName()), clanEntry);
            Bson roleEntry = new Document("$set", new Document("role", "LEADER"));
            this.playerDataCollection.updateMany(new Document("player", leader.getName()), roleEntry);
        });
    }

    @Override
    public void deleteClan(String id) {
        CompletableFuture.runAsync(() -> {
            this.getClan(id, clan -> clan.getMembers().forEach(clanPlayer -> {
                Bson clanEntry = new Document("$set", new Document("clan", "null"));
                this.playerDataCollection.updateMany(new Document("player", clanPlayer.getPlayer()), clanEntry);
                Bson roleEntry = new Document("$set", new Document("role", "MEMBER"));
                this.playerDataCollection.updateMany(new Document("player", clanPlayer.getPlayer()), roleEntry);
                ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
            }));
            this.clanDataCollection.findOneAndDelete(new Document("id", id));
        });
    }

    @Override
    public void getClan(String id, Consumer<Clan> clan) {
        Document document = this.clanDataCollection.find(new Document("id", id)).first();
        if (document != null) {
            String name = document.getString("name");
            String tag = document.getString("tag");
            List<String> requests = document.getList("requests", String.class);
            Clan.InviteSettings inviteSettings = Clan.InviteSettings.valueOf(document.getString("invite"));
            List<ClanPlayer> clanPlayers = new ArrayList<>();
            for (String s : document.getList("members", String.class)) {
                String[] data = s.split(":");
                this.getPlayer(data[0], e -> clanPlayers.add(new ClanPlayer(data[0], e.getRequests(), id, data[1])));
            }
            clan.accept(new Clan(name, tag, id, inviteSettings, clanPlayers, requests));
        } else clan.accept(null);
    }

    @Override
    public void getClanByTag(String tag, Consumer<Clan> clan) {
        CompletableFuture.runAsync(() -> {
            Document document = this.clanDataCollection.find(new Document("tag", tag)).first();
            if (document != null) {
                this.getClan(document.getString("id"), rawClan -> {
                    clan.accept(rawClan);
                });
            } else clan.accept(null);
        });
    }

    @Override
    public void createUserClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            Document document = this.playerDataCollection.find(new Document("player", player)).first();
            if (document != null) {
                List<String> requests = document.getList("requests", String.class);
                requests.add(id);
                Bson requestEntry = new Document("$set", new Document("requests", requests));
                this.playerDataCollection.updateOne(new Document("player", player), requestEntry);
            }
        });
    }

    @Override
    public void createJoinClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            Document document = this.clanDataCollection.find(new Document("id", id)).first();
            if (document != null) {
                List<String> requests = document.getList("requests", String.class);
                requests.add(player);
                Bson requestEntry = new Document("$set", new Document("requests", requests));
                this.clanDataCollection.updateOne(new Document("id", id), requestEntry);
            }
        });
    }

    @Override
    public void deleteClanRequest(String id, String requester) {
        CompletableFuture.runAsync(() -> {
            Document document = this.clanDataCollection.find(new Document("id", id)).first();
            if (document != null) {
                List<String> requests = document.getList("requests", String.class);
                requests.remove(requester);
                Bson requestEntry = new Document("$set", new Document("requests", requests));
                this.clanDataCollection.updateOne(new Document("id", id), requestEntry);
            }
        });
    }

    @Override
    public void deleteUserClanRequest(String player, String id) {
        CompletableFuture.runAsync(() -> {
            Document document = this.playerDataCollection.find(new Document("player", player)).first();
            if (document != null) {
                List<String> requests = document.getList("requests", String.class);
                requests.remove(id);
                Bson requestEntry = new Document("$set", new Document("requests", requests));
                this.playerDataCollection.updateOne(new Document("player", player), requestEntry);
            }
        });
    }

    @Override
    public void getPlayer(String player, Consumer<ClanPlayer> clanPlayer) {
        Document document = this.playerDataCollection.find(new Document("player", player)).first();
        if (document != null) {
            List<String> requests = document.getList("requests", String.class);
            String clan = document.getString("clan");
            String role = document.getString("role");
            clanPlayer.accept(new ClanPlayer(player, requests, clan, role));
        } else clanPlayer.accept(null);
    }

    @Override
    public void leaveClan(ClanPlayer clanPlayer) {
        CompletableFuture.runAsync(() -> {
            Bson clanEntry = new Document("$set", new Document("clan", "null"));
            this.playerDataCollection.updateMany(new Document("player", clanPlayer.getPlayer()), clanEntry);
            Bson roleEntry = new Document("$set", new Document("role", "MEMBER"));
            this.playerDataCollection.updateMany(new Document("player", clanPlayer.getPlayer()), roleEntry);

            Document document = this.clanDataCollection.find(new Document("id", clanPlayer.getClan())).first();
            if (document != null) {
                List<String> list = document.getList("members", String.class);
                list.remove(clanPlayer.getPlayer() + ":" + clanPlayer.getRole());
                Bson memberEntry = new Document("$set", new Document("members", list));
                this.clanDataCollection.updateMany(new Document("id", clanPlayer.getClan()), memberEntry);
            }

            ClanSystem.getInstance().getCachedClanPlayers().remove(clanPlayer.getPlayer());
        });
    }

    @Override
    public void joinClan(String player, Clan clan) {
        CompletableFuture.runAsync(() -> {
            Bson clanEntry = new Document("$set", new Document("clan", clan.getId()));
            this.playerDataCollection.updateMany(new Document("player", player), clanEntry);
            Bson roleEntry = new Document("$set", new Document("role", "MEMBER"));
            this.playerDataCollection.updateMany(new Document("player", player), roleEntry);

            Document document = this.clanDataCollection.find(new Document("id", clan.getId())).first();
            if (document != null) {
                List<String> list = document.getList("members", String.class);
                list.add(player + ":" + "MEMBER");
                Bson memberEntry = new Document("$set", new Document("members", list));
                this.clanDataCollection.updateMany(new Document("id", clan.getId()), memberEntry);

                ClanSystem.getInstance().getCachedClanPlayers().put(player, clan);
            }
        });
    }

    @Override
    public void changeClanRank(ClanPlayer clanPlayer, Clan.ClanRole rank) {
        CompletableFuture.runAsync(() -> {
            Bson roleEntry = new Document("$set", new Document("role", rank.name().toUpperCase()));
            this.playerDataCollection.updateMany(new Document("player", clanPlayer.getPlayer()), roleEntry);

            Document document = this.clanDataCollection.find(new Document("id", clanPlayer.getClan())).first();
            if (document != null) {
                List<String> list = document.getList("members", String.class);
                list.remove(clanPlayer.getPlayer() + ":" + clanPlayer.getRole());
                list.add(clanPlayer.getPlayer() + ":" + rank.name().toUpperCase());
                Bson memberEntry = new Document("$set", new Document("members", list));
                this.clanDataCollection.updateMany(new Document("id", clanPlayer.getClan()), memberEntry);
            }
        });
    }

    @Override
    public void renameClan(String id, String name, String tag) {
        CompletableFuture.runAsync(() -> {
            Bson nameEntry = new Document("$set", new Document("name", name));
            this.clanDataCollection.updateMany(new Document("id", id), nameEntry);
            Bson tagEntry = new Document("$set", new Document("tag", tag));
            this.clanDataCollection.updateMany(new Document("id", id), tagEntry);
        });
    }

    @Override
    public void changeInviteSettings(String id, Clan.InviteSettings inviteSettings) {
        CompletableFuture.runAsync(() -> {
            Bson inviteEntry = new Document("$set", new Document("invite", inviteSettings.name().toUpperCase()));
            this.clanDataCollection.updateMany(new Document("id", id), inviteEntry);
        });
    }

    @Override
    public String getProvider() {
        return "MongoDB";
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
