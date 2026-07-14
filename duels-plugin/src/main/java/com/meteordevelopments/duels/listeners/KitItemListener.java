package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.core.arena.ArenaManagerImpl;
import com.meteordevelopments.duels.core.kit.edit.KitEditManager;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.StringUtil;
import com.meteordevelopments.duels.util.compat.Identifiers;
import com.meteordevelopments.duels.util.kitguard.KitGuardManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Prevents players from using kit items outside a duel by checking
 * for an NBT tag stored in the item by Duels.
 */
public class KitItemListener implements Listener {

    private static final String MSG_PLAYER  = "&4[Duels] &cKit item removed — kit contents cannot be used outside a duel."; // Some new AI messages, thx.
    private static final String MSG_CONSOLE = "[KitGuard] %s (%s) triggered %s with kit item '%s' at %s [%d,%d,%d] | item owner: %s (%s)";
    private static final String MSG_ADMIN   = "&c[KitGuard] &f%s &ctriggered &f%s &cwith kit item &f%s &c| owner: &f%s";

    private final DuelsPlugin plugin;
    private final ArenaManagerImpl arenaManager;
    private final Config config;
    private final KitGuardManager guard;

    public KitItemListener(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = plugin.getArenaManager();
        this.config = plugin.getConfiguration();
        this.guard = KitGuardManager.init(plugin);

        if (config.isProtectKitItems())
            plugin.registerListener(this);
    }

    private boolean isExcluded(final Player player) {
        return player.hasPermission(Permissions.ADMIN)
                || arenaManager.isInMatch(player)
                || KitEditManager.getInstance().isEditing(player);
    }

    private boolean isKitItem(final ItemStack item) {
        return item != null && item.getType() != Material.AIR && Identifiers.hasIdentifier(item);
    }

    private void handleViolation(Player player, String eventType, ItemStack item) {
        player.sendMessage(StringUtil.color(MSG_PLAYER));

        int sweptExtra = sweepInventory(player);

        if (!guard.isDetectionEnabled())
            return;

        String ownerUUID = Identifiers.getOwnerUUID(item);
        String ownerName = Identifiers.getOwnerName(item);
        String itemName = item.getType().name();
        Location loc = player.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        if (config.isKitGuardLogDetections()) {
            Log.warn(String.format(MSG_CONSOLE, player.getName(), player.getUniqueId(), eventType, itemName, world, x, y, z, ownerName != null ? ownerName : "?", ownerUUID != null ? ownerUUID   : "?"));
            if (sweptExtra > 0)
                Log.warn(String.format("[KitGuard] Inventory sweep: removed %d additional kit item(s) from %s", sweptExtra, player.getName()));
        }

        guard.logViolation(player, eventType, itemName, ownerUUID, ownerName, world, x, y, z);

        if (config.isKitGuardAlertAdmins()) {
            String adminMsg = StringUtil.color(String.format(MSG_ADMIN, player.getName(), eventType, itemName, ownerName != null ? ownerName : "?"));
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player) && p.hasPermission(Permissions.ADMIN))
                    .forEach(p -> p.sendMessage(adminMsg));
        }
    }

    private int sweepInventory(Player player) {
        int removed = 0;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isKitItem(contents[i])) {
                contents[i] = null;
                removed++;
            }
        }
        inv.setStorageContents(contents);

        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (isKitItem(armor[i])) {
                armor[i] = null;
                armorChanged = true;
                removed++;
            }
        }
        if (armorChanged)
            inv.setArmorContents(armor);

        if (isKitItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            removed++;
        }

        if (isKitItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }

        if (removed > 0)
            player.updateInventory();
        return removed;
    }

    private void handleMachineViolation(Inventory source, ItemStack item) {
        String ownerUUID = Identifiers.getOwnerUUID(item);
        String ownerName = Identifiers.getOwnerName(item);
        String itemName = item.getType().name();

        if (config.isKitGuardLogDetections())
            Log.warn(String.format("[KitGuard] Mechanism attempted to move kit item '%s' | owner: %s (%s)", itemName, ownerName != null ? ownerName : "?", ownerUUID != null ? ownerUUID   : "?"));

        if (guard.isDetectionEnabled() && ownerUUID != null)
            guard.logMachineViolation(ownerUUID, ownerName, "MECHANISM_MOVE", itemName);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < source.getSize(); i++) {
                final ItemStack slot = source.getItem(i);
                if (isKitItem(slot))
                    source.setItem(i, null);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isExcluded(player))
            return;

        Inventory clicked = event.getClickedInventory();
        InventoryAction action = event.getAction();

        // Player's inv
        if (clicked instanceof PlayerInventory) {
            ItemStack item = event.getCurrentItem();

            if (action.equals(InventoryAction.HOTBAR_SWAP) || action.equals(InventoryAction.HOTBAR_MOVE_AND_READD)) { // Suppress 1-9 swap
                int btn = event.getHotbarButton();
                if (btn >= 0) {
                    ItemStack hotbarItem = player.getInventory().getItem(btn);
                    if (isKitItem(hotbarItem)) {
                        event.setCancelled(true);
                        player.getInventory().setItem(btn, null);
                        handleViolation(player, "HOTBAR_SWAP", hotbarItem);
                        return;
                    }
                }
            }

            if (!isKitItem(item))
                return;
            event.setCancelled(true);
            event.setCurrentItem(null);
            handleViolation(player, "INV_CLICK", item);
            return;
        }

        // Another inv
        if (config.isKitGuardBlockChestTransfer() && clicked != null) {
            ItemStack cursor = event.getCursor();
            if (isKitItem(cursor)) {
                event.setCancelled(true);
                ItemStack snapshot = cursor.clone();
                Bukkit.getScheduler().runTask(plugin, () -> { // Burn it down, yeah
                    event.getView().setCursor(null);
                    player.updateInventory();
                });
                handleViolation(player, "CHEST_PLACE", snapshot);
                return;
            }


            if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) { // Suppress 1-9 swap
                int btn = event.getHotbarButton();
                if (btn >= 0) {
                    ItemStack hotbarItem = player.getInventory().getItem(btn);
                    if (isKitItem(hotbarItem)) {
                        event.setCancelled(true);
                        player.getInventory().setItem(btn, null);
                        handleViolation(player, "HOTBAR_SWAP_CHEST", hotbarItem);
                        return;
                    }
                }
            }

            ItemStack slotItem = event.getCurrentItem();
            if (isKitItem(slotItem)) {
                event.setCancelled(true);
                event.setCurrentItem(null);
                handleViolation(player, "CHEST_KIT_ITEM", slotItem);
                return;
            }
        }

        if (config.isKitGuardBlockChestTransfer() // Idk why it highlights as always false
                && action.equals(InventoryAction.MOVE_TO_OTHER_INVENTORY) && clicked instanceof PlayerInventory) {
            ItemStack item = event.getCurrentItem();
            if (!isKitItem(item))
                return;
            event.setCancelled(true);
            event.setCurrentItem(null);
            handleViolation(player, "SHIFT_TO_CHEST", item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (isExcluded(player)) return;
        final ItemStack item = event.getItem();
        if (!isKitItem(item)) return;
        event.setCancelled(true);
        player.getInventory().remove(item);
        handleViolation(player, "INTERACT", item); // Just handleViolation
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (isExcluded(player))
            return;
        Item itemEntity = event.getItem();
        if (!isKitItem(itemEntity.getItemStack())) //
            return;
        event.setCancelled(true);
        itemEntity.remove();
        handleViolation(player, "PICKUP", itemEntity.getItemStack());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(final BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = event.getItemInHand();
        if (!isKitItem(item))
            return;
        event.setCancelled(true);
        player.getInventory().remove(item);
        handleViolation(player, "BLOCK_PLACE", item); // Just handleViolation
    }

    // Item in hand
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (!isKitItem(item))
            return;
        event.setCancelled(true);
        player.getInventory().setItem(event.getNewSlot(), null);
        handleViolation(player, "ITEM_HELD", item);
    }

    // Drop
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerDropItemEvent event) {
        if (!config.isKitGuardBlockDrop())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (!isKitItem(item))
            return;
        ItemStack snapshot = item.clone();
        event.getItemDrop().remove();
        handleViolation(player, "DROP", snapshot);
    }

    // Swapping
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerSwapHandItemsEvent event) {
        if (!config.isKitGuardBlockOffhand())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack main = event.getMainHandItem();
        ItemStack offhand = event.getOffHandItem();
        if (!isKitItem(main) && !isKitItem(offhand))
            return;
        event.setCancelled(true);
        ItemStack offending = null;
        if (isKitItem(main)) {
            offending = main.clone();
            player.getInventory().setItemInMainHand(null);
        }
        if (isKitItem(offhand)) {
            if (offending == null)
                offending = offhand.clone();
            player.getInventory().setItemInOffHand(null);
        }
        handleViolation(player, "OFFHAND_SWAP", offending);
    }

    // Armor_stand
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerArmorStandManipulateEvent event) {
        if (!config.isKitGuardBlockArmorStand())
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack playerItem = event.getPlayerItem();
        ItemStack standItem  = event.getArmorStandItem();
        if (!isKitItem(playerItem) && !isKitItem(standItem))
            return;
        event.setCancelled(true);
        ItemStack offending = null;
        if (isKitItem(playerItem)) {
            offending = playerItem.clone();
            player.getInventory().setItemInMainHand(null);
        }
        if (isKitItem(standItem)) {
            if (offending == null)
                offending = standItem.clone();
            event.getRightClicked().setItem(event.getSlot(), null);
        }
        handleViolation(player, "ARMOR_STAND", offending);
    }

    // Crafting
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PrepareItemCraftEvent event) {
        if (!config.isKitGuardBlockCraft())
            return;
        if (!(event.getView().getPlayer() instanceof Player player))
            return;
        if (isExcluded(player))
            return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (!Identifiers.anyHasIdentifier(matrix))
            return;

        event.getInventory().setResult(null);

        Bukkit.getScheduler().runTask(plugin, () -> { // Burn. It. Down.
            ItemStack[] current = event.getInventory().getMatrix();
            for (int i = 0; i < current.length; i++) {
                if (isKitItem(current[i]))
                    current[i] = null;
            }
            event.getInventory().setMatrix(current);
            player.updateInventory();
        });

        ItemStack offending = findFirst(matrix);
        if (offending != null)
            handleViolation(player, "CRAFT", offending);
    }

    // Mechanisms
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(InventoryMoveItemEvent event) {
        if (!config.isKitGuardBlockChestTransfer())
            return;
        if (!isKitItem(event.getItem()))
            return;
        event.setCancelled(true);
        handleMachineViolation(event.getSource(), event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isExcluded(player))
            return;
        if (!isKitItem(event.getOldCursor()))
            return;

        Inventory top = event.getView().getTopInventory();
        if (top instanceof PlayerInventory)
            return;

        int topSize = top.getSize();
        boolean goesToExternal = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (!goesToExternal)
            return;

        event.setCancelled(true);
        handleViolation(player, "INV_DRAG", event.getOldCursor());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame))
            return;
        Player player = event.getPlayer();
        if (isExcluded(player))
            return;
        ItemStack item = (event.getHand().equals(EquipmentSlot.HAND))
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
        if (!isKitItem(item))
            return;
        event.setCancelled(true);
        handleViolation(player, "ITEM_FRAME", item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(BlockDispenseEvent event) {
        if (!isKitItem(event.getItem()))
            return;
        event.setCancelled(true);

        if (event.getBlock().getState() instanceof Container container) {
            for (int i = 0; i < container.getInventory().getSize(); i++) {
                ItemStack slot = container.getInventory().getItem(i);
                if (isKitItem(slot))
                    container.getInventory().setItem(i, null);
            }
        }

        if (config.isKitGuardLogDetections())
            Log.warn(String.format("[KitGuard] Dispenser attempted to dispense kit item '%s' at %s",
                event.getItem().getType().name(),
                event.getBlock().getLocation()));
    }

    private static ItemStack findFirst(ItemStack[] items) {
        if (items == null)
            return null;
        for (ItemStack item: items) {
            if (Identifiers.hasIdentifier(item))
                return item;
        }
        return null;
    }
}
