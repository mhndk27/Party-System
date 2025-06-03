package com.mhndk27.partysys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.mhndk27.partysys.managers.PartyChatManager;
import com.mhndk27.partysys.utils.MessageUtils;
import com.mhndk27.partysys.utils.TeleportUtils;

/**
 * PartyManager مسؤول عن إدارة الأحزاب (إنشاء، حذف، إضافة/إزالة عضو، نقل القيادة، أدوات API) متكامل
 * مع RoomManager في بلوقن servercore (لا تحذف أي تكامل).
 */
public class PartyManager {

    // ===== Singleton =====
    private static PartyManager instance;

    public PartyManager() {
        instance = this;
    }

    public static PartyManager getInstance() {
        return instance;
    }

    // ===== Party Data =====
    private final Map<UUID, Party> playerPartyMap = new HashMap<>();
    private final Set<Party> parties = new HashSet<>();
    private final Location lobbyLocation = new Location(Bukkit.getWorld("world"), 0.5, 16, 0.5);

    // ===== Party Lookup =====

    /** Get the party of a player */
    public Party getParty(UUID playerUUID) {
        return playerPartyMap.get(playerUUID);
    }

    /** Is the player in a party? */
    public boolean isInParty(UUID playerUUID) {
        return playerPartyMap.containsKey(playerUUID);
    }

    // ===== Party CRUD =====

    /** Create a new party */
    public Party createParty(UUID leaderUUID) {
        if (isInParty(leaderUUID))
            return null;
        Party party = new Party(leaderUUID);
        parties.add(party);
        addParty(party);
        return party;
    }

    /** Add all party members to the map */
    public void addParty(Party party) {
        for (UUID member : party.getMembers()) {
            playerPartyMap.put(member, party);
        }
    }

    /** Remove a party and clean up everything */
    public void removeParty(Party party) {
        for (UUID member : party.getMembers()) {
            playerPartyMap.remove(member);
            PartyChatManager.getInstance().disablePartyChat(member);
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                TeleportUtils.teleportToLocation(p, lobbyLocation);
                p.sendMessage(MessageUtils.info(
                        "You have been teleported to the lobby because the party was disbanded."));
            }
        }
        parties.remove(party);
    }

    // ===== Party Member Management =====

    /** Add a member to the party */
    public boolean addMember(UUID leaderUUID, UUID targetUUID) {
        Party party = getParty(leaderUUID);
        if (party == null || !party.isLeader(leaderUUID))
            return false;
        if (isInParty(targetUUID))
            return false;
        if (party.isFull())
            return false;

        boolean added = party.addMember(targetUUID);
        if (added) {
            addParty(party);
        }
        return added;
    }

    /** Remove a member from the party */
    public boolean removeMember(UUID leaderUUID, UUID targetUUID) {
        Party party = getParty(leaderUUID);
        if (party == null || !party.isLeader(leaderUUID))
            return false;
        if (!party.contains(targetUUID))
            return false;

        boolean removed = party.removeMember(targetUUID);
        if (removed) {
            playerPartyMap.remove(targetUUID);
            PartyChatManager.getInstance().disablePartyChat(targetUUID);
            Player p = Bukkit.getPlayer(targetUUID);
            if (p != null) {
                TeleportUtils.teleportToLocation(p, lobbyLocation);
                p.sendMessage(MessageUtils
                        .info("You have been removed from the party and teleported to the lobby."));
            }
        }
        return removed;
    }

    /** Player leaves the party */
    public boolean leaveParty(UUID playerUUID) {
        Party party = getParty(playerUUID);
        if (party == null)
            return false;

        boolean isLeader = party.isLeader(playerUUID);
        party.removeMember(playerUUID);
        playerPartyMap.remove(playerUUID);
        PartyChatManager.getInstance().disablePartyChat(playerUUID);

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            TeleportUtils.teleportToLocation(player, lobbyLocation);
            player.sendMessage(
                    MessageUtils.info("You left the party and teleported to the lobby."));
        }

        if (party.getMembers().isEmpty()) {
            removeParty(party);
        } else if (isLeader) {
            UUID newLeaderUUID = party.getMembers().iterator().next();
            party.setLeader(newLeaderUUID);
            Player newLeader = Bukkit.getPlayer(newLeaderUUID);
            if (newLeader != null) {
                newLeader.sendMessage(MessageUtils.success(
                        "You have been promoted to party leader because the previous leader left."));
            }
        }
        return true;
    }

    /** Transfer party leadership */
    public boolean transferLeadership(UUID currentLeaderUUID, UUID newLeaderUUID) {
        Party party = getParty(currentLeaderUUID);
        if (party == null || !party.isLeader(currentLeaderUUID))
            return false;
        if (!party.contains(newLeaderUUID))
            return false;

        party.setLeader(newLeaderUUID);
        Player newLeader = Bukkit.getPlayer(newLeaderUUID);
        if (newLeader != null) {
            newLeader.sendMessage(MessageUtils.success("You have been promoted to party leader."));
        }
        return true;
    }

    // ===== API Utilities =====

    /** Is the player in any party? */
    public boolean isInAnyParty(UUID playerUUID) {
        return isInParty(playerUUID);
    }

    /** Get player name from UUID */
    public String getPlayerName(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        return (player != null) ? player.getName() : null;
    }

    /** Get UUID from player name */
    public UUID getUUIDFromName(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return (player != null) ? player.getUniqueId() : null;
    }

    /** Is the player the party leader? */
    public boolean isLeader(UUID uuid) {
        Party party = getParty(uuid);
        return party != null && party.getLeaderUUID().equals(uuid);
    }

    /** Get all party members */
    public List<UUID> getPartyMembers(UUID uuid) {
        Party party = getParty(uuid);
        return party != null ? new ArrayList<>(party.getMembers()) : Collections.emptyList();
    }

    /** Get party by leader */
    public Party getPartyByLeader(UUID leader) {
        Party party = getParty(leader);
        return party != null && party.getLeaderUUID().equals(leader) ? party : null;
    }

    /** Get party leader for a player */
    public UUID getPartyLeader(UUID playerUUID) {
        Party party = getParty(playerUUID);
        return (party != null) ? party.getLeaderUUID() : null;
    }

    /** Get party members for a player */
    public List<UUID> getPartyMembersOfPlayer(UUID playerUUID) {
        Party party = getParty(playerUUID);
        return (party != null) ? new ArrayList<>(party.getMembers()) : Collections.emptyList();
    }

    /** Get party object for a player */
    public Party getPlayerParty(UUID playerUUID) {
        return getParty(playerUUID);
    }

    /** Get party size for a player */
    public int getPartySize(UUID playerUUID) {
        Party party = getParty(playerUUID);
        return (party != null) ? party.getMembers().size() : 0;
    }

    /** Kick a player from their party (external API) */
    public boolean kickPlayerFromParty(UUID targetUUID) {
        Party party = getParty(targetUUID);
        if (party == null)
            return false;
        UUID leaderUUID = party.getLeaderUUID();
        if (leaderUUID.equals(targetUUID)) {
            // Cannot kick the leader with this method
            return false;
        }
        return removeMember(leaderUUID, targetUUID);
    }
}
