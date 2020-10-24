package net.lldv.clansystem.components.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Clan {

    private final String name;
    private final String tag;
    private final String id;
    private final InviteSettings inviteSettings;
    private final List<ClanPlayer> members;
    private final List<String> requests;

    public enum InviteSettings {
        INVITE,
        CLOSED,
        PUBLIC
    }

    public enum ClanRole {
        MEMBER,
        MODERATOR,
        LEADER
    }

}
