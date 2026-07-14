package com.meteordevelopments.duels.gui.options.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.gui.BaseButton;
import com.meteordevelopments.duels.gui.options.OptionsGui;
import com.meteordevelopments.duels.util.inventory.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class DestructibleArenaButton extends BaseButton {
    private final OptionsGui parent;
    private final KitImpl kit;

    public DestructibleArenaButton(DuelsPlugin plugin, OptionsGui parent, KitImpl kit) {
        super(plugin, ItemBuilder.of(Material.END_CRYSTAL).build());
        this.parent = parent;
        this.kit = kit;
        refresh();
    }

    private void refresh() {
        boolean enabled = kit.getDestructibleArena().isEnabled();
        setDisplayName("&dРазрушаемая арена", lang);
        setGlow(enabled);
        setLore(lang, enabled ? List.of(
                "&7Разрешает разрушение арены",
                "&7и последующее восстановление.",
                "",
                "&aСостояние: включено",
                "",
                "&eЛКМ: открыть настройки",
                "&cShift + ЛКМ: выключить"
        ) : List.of(
                "&7Разрешает разрушение арены",
                "&7для Crystal, Anchor, Minecart",
                "&7и других PvP-китов.",
                "",
                "&cСостояние: выключено",
                "",
                "&eЛКМ: включить"
        ));
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event) {
        if (!kit.getDestructibleArena().isEnabled()) {
            kit.getDestructibleArena().setEnabled(true);
            kit.saveDestructibleArena();
            refresh();
            parent.update(player, this);
            return;
        }
        if (event.isShiftClick() && event.isLeftClick()) {
            kit.getDestructibleArena().setEnabled(false);
            kit.saveDestructibleArena();
            refresh();
            parent.update(player, this);
            return;
        }
        if (event.isLeftClick()) {
            plugin.getGuiListener().addGui(player, new DestructibleArenaSettingsGui(plugin, player, kit), true).open(player);
        }
    }
}
