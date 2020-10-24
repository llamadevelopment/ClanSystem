package net.lldv.clansystem.components.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ClanPlayer {

    private final String player;
    private final List<String> requests;
    private final String clan;
    private final String role;

}
