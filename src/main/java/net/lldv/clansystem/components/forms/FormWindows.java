package net.lldv.clansystem.components.forms;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementDropdown;
import cn.nukkit.form.element.ElementInput;
import net.lldv.clansystem.components.api.ClanSystemAPI;
import net.lldv.clansystem.components.data.Clan;
import net.lldv.clansystem.components.data.ClanPlayer;
import net.lldv.clansystem.components.forms.custom.CustomForm;
import net.lldv.clansystem.components.forms.modal.ModalForm;
import net.lldv.clansystem.components.forms.simple.SimpleForm;
import net.lldv.clansystem.components.language.Language;
import net.lldv.clansystem.components.provider.Provider;

import java.util.Arrays;

public class FormWindows {

    private final Provider provider;

    public FormWindows(Provider provider) {
        this.provider = provider;
    }

    public void openClanMenu(Player player) {
        if (!this.provider.playerIsInClan(player.getName())) {
            this.provider.getPlayer(player.getName(), clanPlayer -> {
                SimpleForm form = new SimpleForm.Builder(Language.getNP("no-clan-menu-title"), Language.getNP("no-clan-menu-content"))
                        .addButton(new ElementButton(Language.getNP("no-clan-create-clan")), this::openCreateClan)
                        .addButton(new ElementButton(Language.getNP("no-clan-join-clan")), this::openJoinClan)
                        .addButton(new ElementButton(Language.getNP("no-clan-clan-requests", clanPlayer.getRequests().size())), e -> this.openClanRequests(player, clanPlayer))
                        .build();
                form.send(player);
            });
        } else {
            this.provider.getPlayer(player.getName(), clanPlayer -> this.provider.getClan(clanPlayer.getClan(), clan -> {
                SimpleForm.Builder form = new SimpleForm.Builder(Language.getNP("clan-menu-title"), Language.getNP("clan-menu-content", clan.getName(), clan.getTag(), clan.getMembers().size()));
                form.addButton(new ElementButton(Language.getNP("clan-menu-members", clan.getMembers().size())), e -> this.openClanMembers(player, clan));
                if (clanPlayer.getRole().equals("MODERATOR") || clanPlayer.getRole().equals("LEADER")) {
                    form.addButton(new ElementButton(Language.getNP("clan-menu-requests", clan.getRequests().size())), e -> this.openClanRequests(player, clan));
                    form.addButton(new ElementButton(Language.getNP("clan-menu-invite")), e -> this.openClanInvite(player, clan));
                }
                if (clanPlayer.getRole().equals("LEADER")) {
                    form.addButton(new ElementButton(Language.getNP("clan-menu-settings")), e -> this.openClanSettings(player, clan));
                } else {
                    form.addButton(new ElementButton(Language.getNP("clan-menu-leave")), e -> {
                        this.provider.leaveClan(clanPlayer);
                        player.sendMessage(Language.get("clan-left"));
                        ClanSystemAPI.broadcastClanMessage(clan, Language.get("broadcast-member-left", player.getName()));
                    });
                }
                SimpleForm finalForm = form.build();
                finalForm.send(player);
            }));
        }
    }

    public void openClanRequests(Player player, ClanPlayer clanPlayer) {
        SimpleForm.Builder form = new SimpleForm.Builder(Language.getNP("user-requests-menu-title"), Language.getNP("user-requests-menu-content"));
        if (clanPlayer.getRequests() != null && !clanPlayer.getRequests().toString().equals("")) {
            clanPlayer.getRequests().forEach(requests -> this.provider.getClan(requests, clan -> {
                form.addButton(new ElementButton(Language.getNP("user-requests-list-button", clan.getName(), clan.getTag())), e -> this.openClanRequest(player, clan));
            }));
        }
        form.addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu);
        SimpleForm finalForm = form.build();
        finalForm.send(player);
    }

    public void openClanRequest(Player player, Clan clan) {
        SimpleForm form = new SimpleForm.Builder(Language.getNP("user-request-menu-title"), Language.getNP("user-request-menu-content", clan.getName(), clan.getTag(), clan.getMembers().size()))
                .addButton(new ElementButton(Language.getNP("user-request-accept")), e -> {
                    if (this.provider.playerIsInClan(player.getName())) {
                        player.sendMessage(Language.get("already-in-clan"));
                        return;
                    }
                    if (!this.provider.clanNameExists(clan.getName())) {
                        player.sendMessage(Language.get("clan-not-found"));
                        return;
                    }
                    this.provider.joinClan(player.getName(), clan);
                    this.provider.deleteUserClanRequest(player.getName(), clan.getId());
                    player.sendMessage(Language.get("request-accepted", clan.getTag()));
                    ClanSystemAPI.broadcastClanMessage(clan, Language.get("broadcast-member-joined", player.getName()));
                })
                .addButton(new ElementButton(Language.getNP("user-request-deny")), e -> {
                    this.provider.deleteUserClanRequest(player.getName(), clan.getId());
                    player.sendMessage(Language.get("request-denied", clan.getTag()));
                })
                .addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu)
                .build();
        form.send(player);
    }

    public void openCreateClan(Player player) {
        CustomForm form = new CustomForm.Builder(Language.getNP("create-clan-menu-title"))
                .addElement(new ElementInput(Language.getNP("create-clan-name-info"), Language.getNP("create-clan-name-placeholder")))
                .addElement(new ElementInput(Language.getNP("create-clan-tag-info"), Language.getNP("create-clan-tag-placeholder")))
                .addElement(new ElementDropdown(Language.getNP("create-clan-invite-settings"), Arrays.asList("INVITE", "CLOSED", "PUBLIC"), 0))
                .onSubmit(((e, r) -> {
                    String name = r.getInputResponse(0);
                    String tag = r.getInputResponse(1);
                    if (name.isEmpty() || tag.isEmpty()) {
                        player.sendMessage(Language.get("name-or-tag-empty"));
                        return;
                    }
                    if (name.length() > 25 || tag.length() > 5) {
                        player.sendMessage(Language.get("name-or-tag-too-long"));
                        return;
                    }
                    if (this.provider.clanNameExists(name)) {
                        player.sendMessage(Language.get("clan-name-already-exists"));
                        return;
                    }
                    if (this.provider.clanTagExists(tag)) {
                        player.sendMessage(Language.get("clan-tag-already-exists"));
                        return;
                    }
                    if (this.provider.playerIsInClan(player.getName())) {
                        player.sendMessage(Language.get("already-in-clan"));
                        return;
                    }
                    String inviteSettings = r.getDropdownResponse(2).getElementContent();
                    this.provider.createClan(name.replace(" ", ""), tag.replace(" ", ""), player, inviteSettings);
                    player.sendMessage(Language.get("clan-created", name, tag));
                }))
                .build();
        form.send(player);
    }

    public void openJoinClan(Player player) {
        CustomForm form = new CustomForm.Builder(Language.getNP("join-clan-menu-title"))
                .addElement(new ElementInput(Language.getNP("join-clan-name-info"), Language.getNP("join-clan-name-placeholder")))
                .onSubmit((e, r) -> {
                    String tag = r.getInputResponse(0);
                    if (!this.provider.clanTagExists(tag)) {
                        player.sendMessage(Language.get("clan-not-found"));
                        return;
                    }
                    this.provider.getClanByTag(tag, clan -> {
                        if (this.provider.playerIsInClan(player.getName())) {
                            player.sendMessage(Language.get("already-in-clan"));
                            return;
                        }
                        if (clan.getRequests().contains(player.getName())) {
                            player.sendMessage(Language.get("request-already-sent"));
                            return;
                        }
                        if (clan.getInviteSettings() == Clan.InviteSettings.CLOSED) {
                            player.sendMessage(Language.get("clan-invite-closed"));
                            return;
                        }
                        if (clan.getInviteSettings() == Clan.InviteSettings.INVITE) {
                            this.provider.createJoinClanRequest(player.getName(), clan.getId());
                            ClanSystemAPI.broadcastClanMessage(clan, Clan.ClanRole.LEADER, Language.get("broadcast-invite-request", player.getName()));
                            ClanSystemAPI.broadcastClanMessage(clan, Clan.ClanRole.MODERATOR, Language.get("broadcast-invite-request", player.getName()));
                            player.sendMessage(Language.get("invite-sent", tag));
                            return;
                        }
                        this.provider.joinClan(player.getName(), clan);
                        ClanSystemAPI.broadcastClanMessage(clan, Language.get("broadcast-member-joined", player.getName()));
                        player.sendMessage(Language.get("clan-joined", tag));
                    });
                })
                .build();
        form.send(player);
    }

    public void openClanInvite(Player player, Clan clan) {
        CustomForm form = new CustomForm.Builder(Language.getNP("clan-invite-menu-title"))
                .addElement(new ElementInput(Language.getNP("clan-invite-player-info"), Language.getNP("clan-invite-player-placeholder")))
                .onSubmit((e, r) -> {
                    String name = r.getInputResponse(0);;
                    if (!this.provider.userExists(name)) {
                        player.sendMessage(Language.get("user-not-found"));
                        return;
                    }
                    this.provider.getPlayer(name, clanPlayer -> {
                        if (this.provider.playerIsInClan(name)) {
                            player.sendMessage(Language.get("user-already-in-clan"));
                            return;
                        }
                        if (clanPlayer.getRequests().contains(clan.getId())) {
                            player.sendMessage(Language.get("user-request-already-sent"));
                            return;
                        }
                        this.provider.createUserClanRequest(name, clan.getId());
                        player.sendMessage(Language.get("user-request-sent", name));
                        Player player1 = Server.getInstance().getPlayer(name);
                        if (player1 != null) player1.sendMessage(Language.get("new-clan-invite", clan.getTag()));
                    });
                })
                .build();
        form.send(player);
    }

    public void openClanMembers(Player player, Clan clan) {
        SimpleForm.Builder form = new SimpleForm.Builder(Language.getNP("clan-members-menu-title"), Language.getNP("clan-members-menu-content"));
        if (clan.getRequests() != null && !clan.getRequests().toString().equals("")) {
            clan.getMembers().forEach(clanPlayer -> form.addButton(new ElementButton(Language.getNP("clan-members-list-button", clanPlayer.getPlayer(), clanPlayer.getRole())), e -> this.openClanMember(player, clanPlayer)));
        }
        form.addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu);
        SimpleForm finalForm = form.build();
        finalForm.send(player);
    }

    public void openClanMember(Player player, ClanPlayer clanPlayer) {
        this.provider.getPlayer(player.getName(), viewer -> {
            SimpleForm.Builder form = new SimpleForm.Builder(Language.getNP("clan-member-menu-title"), Language.getNP("clan-member-menu-content", clanPlayer.getPlayer(), clanPlayer.getRole()));
            if (viewer.getRole().equals("MODERATOR") || viewer.getRole().equals("LEADER")) {
                if (!clanPlayer.getRole().equals("LEADER")) {
                    form.addButton(new ElementButton(Language.getNP("clan-member-kick")), e -> {
                        this.provider.leaveClan(clanPlayer);
                        player.sendMessage(Language.get("member-kicked", clanPlayer.getPlayer()));
                    });
                }
            }
            if (viewer.getRole().equals("LEADER") && !clanPlayer.getRole().equals("LEADER")) {
                if (clanPlayer.getRole().equals("MEMBER")) {
                    form.addButton(new ElementButton(Language.getNP("clan-member-promote")), e -> {
                        this.provider.changeClanRank(clanPlayer, Clan.ClanRole.MODERATOR);
                        player.sendMessage(Language.get("member-promoted", clanPlayer.getPlayer()));
                    });
                } else {
                    form.addButton(new ElementButton(Language.getNP("clan-member-demote")), e -> {
                        this.provider.changeClanRank(clanPlayer, Clan.ClanRole.MEMBER);
                        player.sendMessage(Language.get("member-demoted", clanPlayer.getPlayer()));
                    });
                }
            }
            form.addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu);
            SimpleForm finalForm = form.build();
            finalForm.send(player);
        });
    }

    public void openClanRequests(Player player, Clan clan) {
        SimpleForm.Builder form = new SimpleForm.Builder(Language.getNP("clan-requests-menu-title"), Language.getNP("clan-requests-menu-content"));
        clan.getRequests().forEach(request -> form.addButton(new ElementButton(Language.getNP("clan-requests-list-button", request)), e -> this.openClanRequest(player, request, clan)));
        form.addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu);
        SimpleForm finalForm = form.build();
        finalForm.send(player);
    }

    public void openClanRequest(Player player, String requester, Clan clan) {
        ModalForm form = new ModalForm.Builder(Language.getNP("clan-request-menu-title"), Language.getNP("clan-request-menu-content", requester),
                Language.getNP("clan-request-accept"), Language.getNP("clan-request-deny"))
                .onYes(e -> {
                    if (this.provider.playerIsInClan(requester)) {
                        player.sendMessage(Language.get("user-already-in-clan"));
                        this.provider.deleteClanRequest(clan.getId(), requester);
                        return;
                    }
                    this.provider.joinClan(requester, clan);
                    this.provider.deleteClanRequest(clan.getId(), requester);
                    player.sendMessage(Language.get("request-accepted", requester));
                    Player player1 = Server.getInstance().getPlayer(requester);
                    if (player1 != null) player1.sendMessage(Language.get("request-was-accepted", clan.getTag()));
                    ClanSystemAPI.broadcastClanMessage(clan, Language.get("broadcast-member-joined", player.getName()));
                })
                .onNo(e -> {
                    this.provider.deleteClanRequest(clan.getId(), requester);
                    player.sendMessage(Language.get("request-denied", requester));
                })
                .build();
        form.send(player);
    }

    public void openClanSettings(Player player, Clan clan) {
        SimpleForm form = new SimpleForm.Builder(Language.getNP("clan-settings-menu-title"), Language.getNP("clan-settings-menu-content"))
                .addButton(new ElementButton(Language.getNP("clan-settings-delete")), e -> {
                    ModalForm modalForm = new ModalForm.Builder(Language.getNP("clan-delete-menu-title"), Language.getNP("clan-delete-menu-content"),
                            Language.getNP("clan-delete-accept"), Language.getNP("back-button"))
                            .onYes(f -> {
                                this.provider.deleteClan(clan.getId());
                                player.sendMessage(Language.get("clan-deleted", clan.getName()));
                            })
                            .onNo(f -> this.openClanSettings(player, clan))
                            .build();
                    modalForm.send(e);
                })
                .addButton(new ElementButton(Language.getNP("clan-settings-rename")), e -> {
                    CustomForm customForm = new CustomForm.Builder(Language.getNP("clan-rename-menu-title"))
                            .addElement(new ElementInput(Language.getNP("create-clan-name-info"), Language.getNP("create-clan-name-placeholder")))
                            .addElement(new ElementInput(Language.getNP("create-clan-tag-info"), Language.getNP("create-clan-tag-placeholder")))
                            .onSubmit((f, r) -> {
                                String name = r.getInputResponse(0);
                                String tag = r.getInputResponse(1);
                                if (name.isEmpty() || tag.isEmpty()) {
                                    player.sendMessage(Language.get("name-or-tag-empty"));
                                    return;
                                }
                                if (name.length() > 25 || tag.length() > 5) {
                                    player.sendMessage(Language.get("name-or-tag-too-long"));
                                    return;
                                }
                                if (this.provider.clanNameExists(name)) {
                                    player.sendMessage(Language.get("clan-name-already-exists"));
                                    return;
                                }
                                if (this.provider.clanTagExists(tag)) {
                                    player.sendMessage(Language.get("clan-tag-already-exists"));
                                    return;
                                }
                                this.provider.renameClan(clan.getId(), name, tag);
                                player.sendMessage(Language.get("clan-renamed", name, tag));
                            })
                            .build();
                    customForm.send(e);
                })
                .addButton(new ElementButton(Language.getNP("clan-settings-invite")), e -> {
                    CustomForm customForm = new CustomForm.Builder(Language.getNP("clan-invitesettings-menu-title"))
                            .addElement(new ElementDropdown(Language.getNP("create-clan-invite-settings"), Arrays.asList("INVITE", "CLOSED", "PUBLIC"), 0))
                            .onSubmit((f, r) -> {
                                this.provider.changeInviteSettings(clan.getId(), Clan.InviteSettings.valueOf(r.getDropdownResponse(0).getElementContent().toUpperCase()));
                                player.sendMessage(Language.get("clan-invitesettings-changed", r.getDropdownResponse(0).getElementContent().toUpperCase()));
                            })
                            .build();
                    customForm.send(e);
                })
                .addButton(new ElementButton(Language.getNP("back-button")), this::openClanMenu)
                .build();
        form.send(player);
    }

}
