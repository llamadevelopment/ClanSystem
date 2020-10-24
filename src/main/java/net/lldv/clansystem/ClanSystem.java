package net.lldv.clansystem;

import cn.nukkit.plugin.PluginBase;
import com.creeperface.nukkit.placeholderapi.api.PlaceholderAPI;
import com.creeperface.nukkit.placeholderapi.placeholder.VisitorSensitivePlaceholder;
import lombok.Getter;
import net.lldv.clansystem.commands.ClanCommand;
import net.lldv.clansystem.components.api.ClanSystemAPI;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.forms.FormListener;
import net.lldv.clansystem.components.forms.FormWindows;
import net.lldv.clansystem.components.language.Language;
import net.lldv.clansystem.components.provider.MongodbProvider;
import net.lldv.clansystem.components.provider.MySqlProvider;
import net.lldv.clansystem.components.provider.Provider;
import net.lldv.clansystem.components.provider.YamlProvider;
import net.lldv.clansystem.listeners.EventListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ClanSystem extends PluginBase {

    private final Map<String, Provider> providers = new HashMap<>();
    public Provider provider;

    @Getter
    private static ClanSystem instance;

    @Getter
    private FormWindows formWindows;

    @Getter
    private boolean usePlaceholder;

    @Getter
    private PlaceholderAPI placeholderAPI;

    @Getter
    private HashMap<String, Clan> cachedClanPlayers;

    @Override
    public void onEnable() {
        try {
            instance = this;
            this.saveDefaultConfig();
            this.providers.put("MongoDB", new MongodbProvider());
            this.providers.put("MySql", new MySqlProvider());
            this.providers.put("Yaml", new YamlProvider());
            if (!this.providers.containsKey(this.getConfig().getString("Provider"))) {
                this.getLogger().error("§4Please specify a valid provider: Yaml, MySql, MongoDB");
                return;
            }
            this.provider = this.providers.get(getConfig().getString("Provider"));
            this.provider.connect(this);
            this.getLogger().info("§aSuccessfully loaded " + this.provider.getProvider() + " provider.");
            ClanSystemAPI.setProvider(this.provider);
            this.formWindows = new FormWindows(this.provider);
            this.usePlaceholder = this.getConfig().getBoolean("Settings.UsePlaceholder");
            this.cachedClanPlayers = new HashMap<>();
            Language.init();
            this.loadPlugin();
            this.getLogger().info("§aPlugin successfully started.");
        } catch (Exception e) {
            e.printStackTrace();
            this.getLogger().error("§4Failed to load ClanSystem.");
        }
    }

    private void loadPlugin() {
        this.getServer().getPluginManager().registerEvents(new FormListener(), this);
        this.getServer().getPluginManager().registerEvents(new EventListener(this), this);

        this.getServer().getCommandMap().register("clansystem", new ClanCommand(this));

        if (this.isUsePlaceholder()) {
            this.placeholderAPI = PlaceholderAPI.getInstance();
            this.placeholderAPI.registerPlaceholder(new VisitorSensitivePlaceholder<>("clantag", 60, true, Collections.singleton("clta"), false, (player, f) -> {
                if (this.cachedClanPlayers.get(player.getName()) != null) {
                    return this.cachedClanPlayers.get(player.getName()).getTag();
                }
                return "";
            }));
            this.placeholderAPI.registerPlaceholder(new VisitorSensitivePlaceholder<>("clanname", 60, true, Collections.singleton("clna"), false, (player, f) -> {
                if (this.cachedClanPlayers.get(player.getName()) != null) {
                    return this.cachedClanPlayers.get(player.getName()).getName();
                }
                return "";
            }));
        }
    }

    @Override
    public void onDisable() {
        this.provider.disconnect(this);
    }

}
