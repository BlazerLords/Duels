package com.meteordevelopments.duels.gui.options.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaConfig;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.gui.BaseButton;
import com.meteordevelopments.duels.util.gui.SinglePageGui;
import com.meteordevelopments.duels.util.inventory.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class DestructibleArenaSettingsGui extends SinglePageGui<DuelsPlugin> {
    private final DuelsPlugin plugin;
    private final Player owner;
    private final KitImpl kit;
    private boolean materialEditorSelected;

    public DestructibleArenaSettingsGui(DuelsPlugin plugin, Player owner, KitImpl kit) {
        super(plugin, "Настройки разрушаемой арены", 6);
        this.plugin = plugin;
        this.owner = owner;
        this.kit = kit;
        DestructibleArenaConfig c = kit.getDestructibleArena();
        int slot = 0;
        set(slot++, toggle(plugin, kit, "Установка блоков", Material.GRASS_BLOCK, DestructibleArenaConfig::isAllowBlockPlace, c::setAllowBlockPlace));
        set(slot++, toggle(plugin, kit, "Ломание блоков", Material.DIAMOND_PICKAXE, DestructibleArenaConfig::isAllowBlockBreak, c::setAllowBlockBreak));
        set(slot++, toggle(plugin, kit, "Взрывы", Material.TNT, DestructibleArenaConfig::isAllowExplosions, c::setAllowExplosions));
        set(slot++, toggle(plugin, kit, "Кристаллы", Material.END_CRYSTAL, DestructibleArenaConfig::isAllowEndCrystals, c::setAllowEndCrystals));
        set(slot++, toggle(plugin, kit, "Якоря", Material.RESPAWN_ANCHOR, DestructibleArenaConfig::isAllowRespawnAnchors, c::setAllowRespawnAnchors));
        set(slot++, toggle(plugin, kit, "TNT-вагонетки", Material.TNT_MINECART, DestructibleArenaConfig::isAllowTntMinecarts, c::setAllowTntMinecarts));
        set(slot++, toggle(plugin, kit, "TNT", Material.TNT, DestructibleArenaConfig::isAllowTnt, c::setAllowTnt));
        set(slot++, toggle(plugin, kit, "Кровати", Material.RED_BED, DestructibleArenaConfig::isAllowBedExplosions, c::setAllowBedExplosions));
        set(slot++, toggle(plugin, kit, "Паутина", Material.COBWEB, DestructibleArenaConfig::isAllowCobwebs, c::setAllowCobwebs));
        set(slot++, toggle(plugin, kit, "Рельсы", Material.RAIL, DestructibleArenaConfig::isAllowRails, c::setAllowRails));
        set(slot++, toggle(plugin, kit, "Жидкости", Material.WATER_BUCKET, DestructibleArenaConfig::isAllowLiquids, c::setAllowLiquids));
        set(slot++, toggle(plugin, kit, "Поршни", Material.PISTON, DestructibleArenaConfig::isAllowPistons, c::setAllowPistons));
        set(slot++, toggle(plugin, kit, "Огонь", Material.FLINT_AND_STEEL, DestructibleArenaConfig::isAllowFire, c::setAllowFire));
        set(slot++, toggle(plugin, kit, "Выпадение блоков", Material.OBSIDIAN, cfg -> !cfg.isSuppressBlockDrops(), value -> c.setSuppressBlockDrops(!value)));
        set(slot++, toggle(plugin, kit, "Выпадение сущностей", Material.MINECART, cfg -> !cfg.isSuppressEntityDrops(), value -> c.setSuppressEntityDrops(!value)));
        set(slot++, toggle(plugin, kit, "Восстановление после матча", Material.CLOCK, DestructibleArenaConfig::isRestoreAfterMatch, c::setRestoreAfterMatch));
        set(slot, toggle(plugin, kit, "Очистка сущностей", Material.LAVA_BUCKET, DestructibleArenaConfig::isCleanupEntities, c::setCleanupEntities));
        set(45, new MaterialEditorButton(plugin, kit));
        set(46, new ArenaBreakingInfoButton(plugin));
    }

    private ToggleButton toggle(DuelsPlugin plugin, KitImpl kit, String name, Material material,
                                Predicate<DestructibleArenaConfig> getter, Consumer<Boolean> setter) {
        return new ToggleButton(plugin, kit, name, material, getter, setter);
    }

    @Override
    public void on(Player player, Inventory inventory, InventoryCloseEvent event) {
        plugin.getGuiListener().removeGui(owner, this);
    }

    @Override
    public void on(Player player, Inventory top, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();

        if (clicked == null) {
            return;
        }

        event.setCancelled(true);

        if (clicked.equals(top)) {
            com.meteordevelopments.duels.util.gui.Button<DuelsPlugin> button = get(inventory, event.getSlot());

            if (button != null) {
                button.onClick(player, event);
            }

            return;
        }

        if (!materialEditorSelected) {
            player.sendMessage(com.meteordevelopments.duels.util.StringUtil.color("&eСначала выберите &fБлоки для установки&e."));
            return;
        }

        Material material = materialFrom(event.getCurrentItem());

        if (material == null) {
            player.sendMessage(com.meteordevelopments.duels.util.StringUtil.color("&cВыберите обычный блок в своём инвентаре."));
            return;
        }

        toggleMaterial(material, player);
        updateMaterialButtons();
    }

    private static final class ToggleButton extends BaseButton {
        private final KitImpl kit;
        private final String name;
        private final Predicate<DestructibleArenaConfig> getter;
        private final Consumer<Boolean> setter;

        private ToggleButton(DuelsPlugin plugin, KitImpl kit, String name, Material material,
                             Predicate<DestructibleArenaConfig> getter, Consumer<Boolean> setter) {
            super(plugin, ItemBuilder.of(material).build());
            this.kit = kit;
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            refresh();
        }

        private void refresh() {
            boolean enabled = getter.test(kit.getDestructibleArena());
            setDisplayName((enabled ? "&a" : "&c") + name, lang);
            setGlow(enabled);
            setLore(lang, "", enabled ? "&aСостояние: включено" : "&cСостояние: выключено", "", "&eЛКМ: переключить");
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            setter.accept(!getter.test(kit.getDestructibleArena()));
            kit.saveDestructibleArena();
            refresh();
            player.getOpenInventory().setItem(event.getSlot(), getDisplayed());
        }
    }

    private void selectMaterialEditor(Player player) {
        this.materialEditorSelected = true;
        updateMaterialButtons();
        player.sendMessage(com.meteordevelopments.duels.util.StringUtil.color(
                "&aВыбран список блоков для установки. &7Нажмите блок в своём инвентаре."));
    }

    private void toggleMaterial(Material material, Player player) {
        Set<Material> materials = kit.getDestructibleArena().getAllowedPlaceMaterials();
        boolean added = materials.add(material);

        if (!added) {
            materials.remove(material);
        }

        kit.saveDestructibleArena();
        player.sendMessage(com.meteordevelopments.duels.util.StringUtil.color((added ? "&aДобавлено: &f" : "&cУдалено: &f") + material.name()));
    }

    private Material materialFrom(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.getType().isBlock()) {
            return null;
        }

        return item.getType();
    }

    private void updateMaterialButtons() {
        MaterialEditorButton place = (MaterialEditorButton) get(inventory, 45);

        if (place != null) {
            place.refresh();
            inventory.setItem(45, place.getDisplayed());
        }
    }

    private final class MaterialEditorButton extends BaseButton {
        private final KitImpl kit;

        private MaterialEditorButton(DuelsPlugin plugin, KitImpl kit) {
            super(plugin, ItemBuilder.of(Material.GRASS_BLOCK).build());
            this.kit = kit;
            refresh();
        }

        private Set<Material> materials() {
            return kit.getDestructibleArena().getAllowedPlaceMaterials();
        }

        private void refresh() {
            setDisplayName((materialEditorSelected ? "&a" : "&e") + "Блоки для установки", lang);
            setGlow(materialEditorSelected);
            List<String> lore = new ArrayList<>();
            lore.add("&7Материалов: &f" + materials().size());
            materials().stream().sorted(Comparator.comparing(Enum::name)).limit(12)
                    .forEach(material -> lore.add("&8- &f" + material.name()));
            if (materials().size() > 12) lore.add("&8... и ещё " + (materials().size() - 12));
            lore.add("");
            lore.add(materialEditorSelected ? "&aСписок выбран." : "&eЛКМ: выбрать список");
            lore.add("&7После выбора нажмите блок");
            lore.add("&7в своём инвентаре.");
            setLore(lang, lore);
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            selectMaterialEditor(player);
        }
    }

    private static final class ArenaBreakingInfoButton extends BaseButton {
        private ArenaBreakingInfoButton(DuelsPlugin plugin) {
            super(plugin, ItemBuilder.of(Material.DIAMOND_PICKAXE).build());
            setDisplayName("&aРазрушение блоков арены", lang);
            setGlow(true);
            setLore(lang,
                    "",
                    "&7Ломаются все блоки внутри",
                    "&7установленных границ арены.",
                    "",
                    "&cЗа пределами арены блоки защищены.",
                    "&7Карта полностью восстановится",
                    "&7после завершения матча.");
        }

        @Override
        public void onClick(Player player, InventoryClickEvent event) {
            player.sendMessage(com.meteordevelopments.duels.util.StringUtil.color(
                    "&aВсе блоки внутри границ арены разрушаются и восстанавливаются после матча."));
        }
    }
}
