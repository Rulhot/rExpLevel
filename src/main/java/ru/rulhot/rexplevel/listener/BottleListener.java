package ru.rulhot.rexplevel.listener;

import lombok.RequiredArgsConstructor;
import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rexplevel.manager.BottleManager;
import ru.rulhot.rexplevel.manager.ConfigManager;
import ru.rulhot.rexplevel.model.BottleSettings.ExpGive;
import ru.rulhot.rexplevel.model.BottleSettings.RedeemMode;
import ru.rulhot.rexplevel.util.MessageUtil;

import java.util.Map;

@RequiredArgsConstructor
public final class BottleListener implements Listener {

    private final @NotNull BottleManager bottles;
    private final @NotNull ConfigManager config;

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(@NotNull PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null || !event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainBottle = bottles.isBottle(main);
        boolean offBottle = bottles.isBottle(off);
        if (!mainBottle && !offBottle) {
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND && mainBottle) {
            return;
        }

        if (!mainBottle && main.getType() == Material.EXPERIENCE_BOTTLE) {
            if (hand == EquipmentSlot.OFF_HAND || throwable(off)) {
                return;
            }
            deny(event);
            drink(player, EquipmentSlot.OFF_HAND);
            return;
        }

        ItemStack used = event.getItem();
        if (!bottles.isBottle(used) || throwable(used)) {
            return;
        }
        deny(event);
        drink(player, hand);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(@NotNull BlockDispenseEvent event) {
        if (!bottles.isBottle(event.getItem())) {
            return;
        }
        if (config.bottle().redeem() != RedeemMode.THROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(@NotNull ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle bottle) || !bottles.isBottle(bottle.getItem())) {
            return;
        }
        if (config.bottle().redeem() != RedeemMode.THROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull ExpBottleEvent event) {
        ItemStack item = event.getEntity().getItem();
        int points = bottles.points(item);
        if (points <= 0) {
            return;
        }
        if (config.returnGlassBottle()) {
            Location location = event.getEntity().getLocation();
            location.getWorld().dropItemNaturally(location, new ItemStack(Material.GLASS_BOTTLE));
        }
        if (config.bottle().expGive() == ExpGive.ADD) {
            event.setExperience(0);
            bottles.spawnLevelOrb(event.getEntity().getLocation(), points, bottles.levels(item));
            return;
        }
        event.setExperience(points);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(@NotNull PlayerPickupExperienceEvent event) {
        ExperienceOrb orb = event.getExperienceOrb();
        int levels = bottles.orbLevels(orb);
        if (levels <= 0) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!bottles.canRedeem(player, orb.getExperience(), levels)) {
            return;
        }
        orb.remove();
        bottles.redeem(player, orb.getExperience(), levels);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(@NotNull ExperienceOrbMergeEvent event) {
        if (bottles.orbLevels(event.getMergeTarget()) > 0 || bottles.orbLevels(event.getMergeSource()) > 0) {
            event.setCancelled(true);
        }
    }

    private boolean throwable(@NotNull ItemStack item) {
        return config.bottle().redeem() == RedeemMode.THROW && item.getType() == Material.EXPERIENCE_BOTTLE;
    }

    private void deny(@NotNull PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    private void drink(@NotNull Player player, @NotNull EquipmentSlot hand) {
        int points = bottles.drink(player, hand);
        if (points < 0) {
            send(player, "drink-full", Map.of());
            return;
        }
        if (points > 0) {
            send(player, "drink", Map.of("points", points));
        }
    }

    private void send(
            @NotNull CommandSender sender,
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        MessageUtil.send(sender, config.formatCommandMessage(key, placeholders));
    }
}
