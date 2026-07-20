package com.meteordevelopments.duels.core.arena;

import com.meteordevelopments.duels.core.countdown.DuelCountdown;
import com.meteordevelopments.duels.core.countdown.party.PartyDuelCountdown;
import com.meteordevelopments.duels.core.match.DuelMatch;
import com.meteordevelopments.duels.core.match.MatchLifecycleState;
import com.meteordevelopments.duels.core.match.party.PartyDuelMatch;
import com.meteordevelopments.duels.core.match.team.TeamDuelMatch;
import com.meteordevelopments.duels.party.Party;
import com.meteordevelopments.duels.core.spectate.SpectatorImpl;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.api.arena.Arena;
import com.meteordevelopments.duels.api.event.arena.ArenaSetPositionEvent;
import com.meteordevelopments.duels.api.event.arena.ArenaStateChangeEvent;
import com.meteordevelopments.duels.api.event.match.MatchEndEvent;
import com.meteordevelopments.duels.api.event.match.MatchEndEvent.Reason;
import com.meteordevelopments.duels.gui.BaseButton;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.arena.destructible.ArenaBounds;
import com.meteordevelopments.duels.core.queue.Queue;
import com.meteordevelopments.duels.setting.Settings;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.compat.Items;
import com.meteordevelopments.duels.util.inventory.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
@SuppressWarnings("deprecation")
public class ArenaImpl extends BaseButton implements Arena {

    private final String name;
    private boolean disabled;
    private final Set<KitImpl> kits = new HashSet<>();
    private final Map<Integer, Location> positions = new HashMap<>();
    private DuelMatch match;
    private ItemStack originalDisplayed; // Stores the display item with {status} placeholder
    @Setter(value = AccessLevel.PACKAGE)
    private boolean removed;
    @Setter
    private DuelCountdown countdown;
    @Setter
    private Location minBound;
    @Setter
    private Location maxBound;

    public ArenaImpl(final DuelsPlugin plugin, final String name, final boolean disabled) {
        super(plugin, ItemBuilder
                .of(Items.EMPTY_MAP)
                .name(plugin.getLang().getMessage("GUI.arena-selector.buttons.arena.name", "name", name), plugin.getLang())
                .lore(plugin.getLang(), plugin.getLang().getMessage("GUI.arena-selector.buttons.arena.lore-default").split("\n"))
                .build()
        );
        this.name = name;
        this.disabled = disabled;
        // Store the original display item with {status} placeholder
        this.originalDisplayed = getDisplayed().clone();
    }

    public ArenaImpl(final DuelsPlugin plugin, final String name) {
        this(plugin, name, false);
    }

    public void refreshGui(final boolean available) {
        // Always refresh from the original display item with {status} placeholder
        if (originalDisplayed == null) {
            return;
        }
        
        // Get the status text to replace {status} placeholder with
        final String statusText = available 
            ? lang.getMessage("GUI.arena-selector.buttons.arena.lore-available")
            : lang.getMessage("GUI.arena-selector.buttons.arena.lore-unavailable");
        
        // Clone the original and replace {status} in the clone
        final ItemStack refreshed = originalDisplayed.clone();
        final ItemMeta meta = refreshed.getItemMeta();
        if (meta != null && meta.hasLore()) {
            final List<String> updatedLore = new ArrayList<>();
            for (final String line : meta.getLore()) {
                // Replace {status} placeholder with the status text
                updatedLore.add(line.replace("{status}", statusText));
            }
            meta.setLore(updatedLore);
            refreshed.setItemMeta(meta);
        }
        
        // Set the refreshed item as the new displayed item
        super.setDisplayed(refreshed);
    }

    @Override
    public void setDisplayed(final ItemStack displayed) {
        // Store the original with {status} placeholder
        this.originalDisplayed = displayed != null ? displayed.clone() : null;
        // Clone again for the displayed version that will be modified by refreshGui
        super.setDisplayed(displayed != null ? displayed.clone() : null);
        // Immediately refresh GUI to show status
        refreshGui(isAvailable());
        arenaManager.saveArenas();
    }

    // Public method to set display item without triggering save (used during loading)
    public void setDisplayedWithoutSave(final ItemStack displayed) {
        // Store the original with {status} placeholder
        this.originalDisplayed = displayed != null ? displayed.clone() : null;
        // Clone again for the displayed version that will be modified by refreshGui
        super.setDisplayed(displayed != null ? displayed.clone() : null);
        // Immediately refresh GUI to show status
        refreshGui(isAvailable());
    }
    
    // Get the original display item with {status} placeholder (used for serialization)
    public ItemStack getDisplayedOriginal() {
        return originalDisplayed != null ? originalDisplayed : getDisplayed();
    }

    @Override
    public void update(final Player player) {
        // Refresh the GUI with current availability status whenever the button is updated
        refreshGui(isAvailable());
    }

    @Nullable
    @Override
    public Location getPosition(final int pos) {
        return positions.get(pos);
    }

    @Override
    public boolean setPosition(@Nullable final Player source, final int pos, @NotNull final Location location) {
        Objects.requireNonNull(location, "location");

        if (pos <= 0 || pos > 2) {
            return false;
        }

        final ArenaSetPositionEvent event = new ArenaSetPositionEvent(source, this, pos, location);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        positions.put(event.getPos(), event.getLocation());
        arenaManager.saveArenas();
        refreshGui(isAvailable());
        return true;
    }

    @Override
    public boolean setPosition(final int pos, @NotNull final Location location) {
        return setPosition(null, pos, location);
    }

    @Override
    public boolean setDisabled(@Nullable final CommandSender source, final boolean disabled) {
        final ArenaStateChangeEvent event = new ArenaStateChangeEvent(source, this, disabled);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        this.disabled = event.isDisabled();
        arenaManager.saveArenas();
        refreshGui(isAvailable());
        return true;
    }

    @Override
    public boolean setDisabled(final boolean disabled) {
        return setDisabled(null, disabled);
    }

    public void setBound(boolean isMin, Location location) {
        if (isMin)
            this.minBound = location.clone();
        else
            this.maxBound = location.clone();
        arenaManager.saveArenas();
    }

    public boolean hasBounds() {
        return minBound != null && maxBound != null;
    }

    @Nullable
    public ArenaBounds getArenaBounds() {
        return ArenaBounds.of(minBound, maxBound);
    }

    public boolean isInBounds(Location loc) {
        ArenaBounds bounds = getArenaBounds();
        if (bounds == null)
            return true;
        return bounds.contains(loc);
    }

    public boolean isBoundless() {
        return kits.isEmpty();
    }

    public boolean isBound(@Nullable final KitImpl kit) {
        return kit != null && kits.contains(kit);
    }

    public void bind(final KitImpl kit) {
        if (isBound(kit)) {
            kits.remove(kit);
        } else {
            kits.add(kit);
        }
        arenaManager.saveArenas();
    }

    @Override
    public boolean isUsed() {
        return match != null;
    }

    public boolean isAvailable() {
        boolean sessionReady = plugin.getDestructibleArenaService() == null
                || plugin.getDestructibleArenaService().getRegistry().byArena(name) == null
                || plugin.getDestructibleArenaService().getRegistry().byArena(name).getState()
                == com.meteordevelopments.duels.arena.destructible.SessionState.READY;
        return !isDisabled() && !isUsed() && sessionReady && getPosition(1) != null && getPosition(2) != null;
    }

    public DuelMatch startMatch(final KitImpl kit, final Map<UUID, List<ItemStack>> items, final Settings settings, final Queue source) {
        if (settings.isPartyDuel()) {
            this.match = new PartyDuelMatch(plugin, this, kit, items, settings.getBet(), source);
        } else if (source != null && source.getTeamSize() > 1) {
            this.match = new TeamDuelMatch(plugin, this, kit, items, settings.getBet(), source);
        } else {
            this.match = new DuelMatch(plugin, this, kit, items, settings.getBet(), source);
        }
        this.match.markPreparing();
        refreshGui(false);
        return match;
    }

    public void endMatch(final UUID winner, final UUID loser, final Reason reason) {
        final DuelMatch endingMatch = match;
        if (endingMatch == null) {
            return; // Match already ended — guard against double endgame
        }
        if (endingMatch.getLifecycleState() != MatchLifecycleState.FINISHING
                && !endingMatch.tryBeginFinishing()) {
            return;
        }
        if (!endingMatch.tryBeginRestoring()) {
            return;
        }
        plugin.getDuelManager().finishMatchAccess(endingMatch);
        spectateManager.stopSpectating(this);

        final MatchEndEvent event = new MatchEndEvent(endingMatch, winner, loser, reason);
        Bukkit.getPluginManager().callEvent(event);

        final Queue source = endingMatch.getSource();
        endingMatch.setFinished();
        final boolean destructibleSession = plugin.getDestructibleArenaService().finish(endingMatch);

        if (!destructibleSession) {
            for(Block block : endingMatch.placedBlocks) {
                block.setType(Material.AIR);
            }

            for(Map.Entry<Location, BlockData> map : endingMatch.brokenBlocks.entrySet()) {
                map.getKey().getBlock().setBlockData(map.getValue());
            }

            for (Entity entity : endingMatch.placedEntities){
                entity.remove();
            }

            for (Block block : endingMatch.liquids) {
                if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    block.setType(Material.AIR);
                }
            }
        }

        if(config.isClearItemsAfterMatch()) {
            endingMatch.droppedItems.forEach(Entity::remove);
        }

        arenaManager.clearPlayerIndex(endingMatch.getAllPlayers(), this);
        match = null;
        if (reason == Reason.PLUGIN_DISABLE || reason == Reason.OTHER) {
            endingMatch.markCancelled();
        } else {
            endingMatch.markCompleted();
        }

        if (source != null) {
            source.update();
            queueManager.getGui().calculatePages();
        }

        refreshGui(true);
    }

    /**
     * Releases a match that failed during preparation without publishing a match-end event.
     * Any destructible-arena session already created for the match is still restored normally.
     *
     * @param expectedMatch match instance that must currently own this arena
     * @return true when the arena was released
     */
    public boolean abortMatchStart(final DuelMatch expectedMatch) {
        if (match != expectedMatch) {
            return false;
        }

        final Queue source = expectedMatch.getSource();
        try {
            plugin.getDestructibleArenaService().finish(expectedMatch);
        } catch (RuntimeException ex) {
            Log.error("Could not clean destructible session after failed match start on arena " + name, ex);
        } finally {
            arenaManager.clearPlayerIndex(expectedMatch.getAllPlayers(), this);
            expectedMatch.markError();
            match = null;
        }

        if (source != null) {
            try {
                source.update();
                queueManager.getGui().calculatePages();
            } catch (RuntimeException ex) {
                Log.error("Could not update queue after failed match start on arena " + name, ex);
            }
        }
        refreshGui(true);
        return true;
    }

    public void startCountdown() {
        this.countdown = match instanceof PartyDuelMatch ? new PartyDuelCountdown(plugin, this, (PartyDuelMatch) match) : new DuelCountdown(plugin, this, match);
        countdown.startCountdown(0L, 20L);
    }

    boolean isCountingComplete() {
        return countdown == null;
    }

    @Override
    public boolean has(@NotNull final Player player) {
        Objects.requireNonNull(player, "player");
        // For team matches, include dead players as they remain in the arena as spectators
        if (isUsed() && match instanceof TeamDuelMatch) {
            return match.getAllPlayers().contains(player);
        }
        return isUsed() && !match.isDead(player);
    }

    public boolean hasParticipant(@NotNull final Player player) {
        Objects.requireNonNull(player, "player");
        return isUsed() && match.getAllPlayers().contains(player);
    }

    public void add(final Player player) {
        if (isUsed()) {
            match.addPlayer(player);
            arenaManager.indexPlayer(player, this);
        }
    }

    public void remove(final Player player) {
        if (isUsed()) {
            match.markAsDead(player);
        }
    }

    public boolean isEndGame() {
        return size() <= 1;
    }

    public int size() {
        return isUsed() ? match.size() : 0;
    }

    public Player first() {
        if (!isUsed()) return null;
        final Set<Player> alive = match.getAlivePlayers();
        return alive.isEmpty() ? null : alive.iterator().next();
    }

    @Override
    public Player getOpponent(final Player player) {
        return isUsed() ? match.getAllPlayers().stream().filter(other -> !player.equals(other)).findFirst().orElse(null) : null;
    }

    public Party getOpponent(final Party party) {
        return isUsed() ? ((PartyDuelMatch) match).getAllParties().stream().filter(other -> !party.equals(other)).findFirst().orElse(null) : null;
    }

    public Set<Player> getPlayers() {
        return isUsed() ? match.getAllPlayers() : Collections.emptySet();
    }

    public void broadcast(final String message) {
        getPlayers().forEach(player -> player.sendMessage(message));
        spectateManager.getSpectatorsImpl(this).stream().map(SpectatorImpl::getPlayer).filter(Objects::nonNull).forEach(player -> player.sendMessage(message));
    }

    @Override
    public void onClick(final Player player) {
        if (!isAvailable()) {
            return;
        }

        final Settings settings = settingManager.getSafely(player);
        final String kitName = settings.getKit() != null ? settings.getKit().getName() : lang.getMessage("GENERAL.none");

        if (!arenaManager.isSelectable(settings.getKit(), this)) {
            lang.sendMessage(player, "ERROR.setting.arena-not-applicable", "kit", kitName, "arena", name);
            return;
        }

        settings.setArena(this);
        settings.openGui(player);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final ArenaImpl arena = (ArenaImpl) other;
        return Objects.equals(name, arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
