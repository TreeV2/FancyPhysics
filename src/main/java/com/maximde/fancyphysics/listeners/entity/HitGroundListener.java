package com.maximde.fancyphysics.listeners.entity;

import com.maximde.fancyphysics.FancyPhysics;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Arrays;
import java.util.List;

public class HitGroundListener implements Listener {
    private final FancyPhysics fancyPhysics;

    public HitGroundListener(FancyPhysics fancyPhysics) {
        this.fancyPhysics = fancyPhysics;
    }

    @EventHandler
    public void onBlockFall(EntityChangeBlockEvent event) {
        if(this.fancyPhysics.getPluginConfig().getDisabledWorldsList().contains(event.getEntity().getLocation().getWorld().getName())) return;
        createParticles(event);
        if (event.getEntityType() != EntityType.FALLING_BLOCK) return;
        var fallingBlock = (FallingBlock) event.getEntity();
        if(fallingBlock.getScoreboardTags().contains("NoDrop")) {
            fallingBlock.remove();
        }
    }

    private void createParticles(EntityChangeBlockEvent event) {
        if(!this.fancyPhysics.getPluginConfig().isBlockCrackOnFall()) return;
        if(!this.fancyPhysics.getPluginConfig().isFallingBlockPhysics()) return;
        if(event.getBlock().getType() != Material.AIR) return;
        if (event.getEntityType() != EntityType.FALLING_BLOCK) return;
        var fallingBlock = (FallingBlock) event.getEntity();
        if(fallingBlock.getScoreboardTags().contains("NoDrop")) {
            fallingBlock.remove();
        }
        if(fallingBlock.getScoreboardTags().contains("DontBreak")) return;
        var material = fallingBlock.getBlockData().getMaterial();
        if(material == Material.DRAGON_EGG) return;
        event.setCancelled(true);
        if(fancyPhysics.getPluginConfig().isNaturalDropsOnExplode()) {
            event.getBlock().getLocation().getBlock().setType(material);
            event.getBlock().getLocation().getBlock().breakNaturally();
        }
        final var loc = event.getBlock().getLocation();
        if(fancyPhysics.getPluginConfig().isSounds()) loc.getWorld().playSound(loc, fallingBlock.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
        this.fancyPhysics.getParticleGenerator().simulateBlockParticles(loc, this.fancyPhysics.getParticleGenerator().getParticleMaterial(material).material());
    }


    private final List<Material> notDropMaterials = Arrays.asList(
            Material.SPAWNER,
            Material.COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.BEDROCK);

}
