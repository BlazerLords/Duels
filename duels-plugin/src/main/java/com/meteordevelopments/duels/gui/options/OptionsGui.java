package com.meteordevelopments.duels.gui.options;

import lombok.Getter;
import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.gui.options.buttons.OptionButton;
import com.meteordevelopments.duels.gui.options.destructible.DestructibleArenaButton;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.core.kit.KitImpl.Characteristic;
import com.meteordevelopments.duels.util.compat.Items;
import com.meteordevelopments.duels.util.gui.SinglePageGui;
import com.meteordevelopments.duels.util.inventory.Slots;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Function;

public class OptionsGui extends SinglePageGui<DuelsPlugin> {

    private final DuelsPlugin plugin;
    private final Player owner;

    public OptionsGui(final DuelsPlugin plugin, final Player player, final KitImpl kit) {
        super(plugin, plugin.getLang().getMessage("GUI.options.title", "kit", kit.getName()), 2);
        this.plugin = plugin;
        this.owner = player;

        int i = 0;

        for (final Option option : Option.values()) {
            set(i++, new OptionButton(plugin, this, kit, option));
        }

        final ItemStack spacing = Items.WHITE_PANE.clone();
        Slots.run(9, 18, slot -> inventory.setItem(slot, spacing));
        set(13, new DestructibleArenaButton(plugin, this, kit));
    }

    @Override
    public void on(final Player player, final Inventory inventory, final InventoryCloseEvent event) {
        plugin.getGuiListener().removeGui(owner, this);
    }

    public enum Option {

        USEPERMISSION("Требовать разрешение", Material.BARRIER, KitImpl::isUsePermission,
                kit -> kit.setUsePermission(!kit.isUsePermission()),
                "Для выбора кита игроку потребуется", "право duels.kits.%kit%."),
        ARENASPECIFIC("Привязка к аренам", Items.EMPTY_MAP, KitImpl::isArenaSpecific,
                kit -> kit.setArenaSpecific(!kit.isArenaSpecific()),
                "Кит можно использовать только", "на привязанных к нему аренах."),
        SOUP("Лечебные супы", Items.MUSHROOM_SOUP, Characteristic.SOUP,
                "Суп восстанавливает здоровье", "при нажатии правой кнопкой мыши."),
        SUMO("Режим сумо", Material.SLIME_BALL, Characteristic.SUMO,
                "Обычный урон отключён.", "Поражение засчитывается в воде или лаве."),
        UHC("Отключить регенерацию", Material.GOLDEN_APPLE, Characteristic.UHC,
                "Естественное восстановление", "здоровья будет отключено."),
        COMBO("Режим комбо", Material.IRON_SWORD, Characteristic.COMBO,
                "Убирает задержку между", "последовательными ударами."),
        LOKA("Усиленный урон", Material.DIAMOND_SWORD, Characteristic.LOKA,
                "Урон игроков увеличивается", "на 33 процента."),
        HUNGER("Отключить голод", Material.COOKED_BEEF, Characteristic.HUNGER,
                "Уровень голода игроков", "не будет уменьшаться."),
        ROUNDS3("Три раунда", Material.GOLD_INGOT, Characteristic.ROUNDS3,
                "Матч проводится до победы", "в двух из трёх раундов.");
        @Getter
        private final Material displayed;
        @Getter
        private final String[] description;
        @Getter
        private final String displayName;

        private final Function<KitImpl, Boolean> getter;
        private final Consumer<KitImpl> setter;

        Option(final String displayName, final Material displayed, final Function<KitImpl, Boolean> getter,
               final Consumer<KitImpl> setter, final String... description) {
            this.displayName = displayName;
            this.displayed = displayed;
            this.description = description;
            this.getter = getter;
            this.setter = setter;
        }

        Option(final String displayName, final Material displayed, final Characteristic characteristic,
               final String... description) {
            this.displayName = displayName;
            this.displayed = displayed;
            this.description = description;
            this.getter = kit -> kit.hasCharacteristic(characteristic);
            this.setter = kit -> kit.toggleCharacteristic(characteristic);
        }

        public boolean get(final KitImpl kit) {
            return getter.apply(kit);
        }

        public void set(final KitImpl kit) {
            setter.accept(kit);
        }
    }
}
