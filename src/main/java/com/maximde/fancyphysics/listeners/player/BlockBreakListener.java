package com.maximde.fancyphysics.listeners.player;

import com.maximde.fancyphysics.FancyPhysics;
import com.maximde.fancyphysics.api.events.TreeBreakEvent;
import com.maximde.fancyphysics.utils.Tree;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public class BlockBreakListener implements Listener {
    private final FancyPhysics fancyPhysics;

    public BlockBreakListener(FancyPhysics fancyPhysics) {
        this.fancyPhysics = fancyPhysics;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        if (this.fancyPhysics.getPluginConfig().getDisabledWorldsList().contains(event.getPlayer().getLocation().getWorld().getName()))
            return;
        if (this.fancyPhysics.getPluginConfig().isEnableSpawnProtection()) {
            if (this.fancyPhysics.getPluginConfig().getSpawn().getWorld() == event.getPlayer().getWorld()) {
                if (this.fancyPhysics.getPluginConfig().getSpawn().distance(event.getPlayer().getLocation()) < this.fancyPhysics.getPluginConfig().getProtectionRadius()) {
                    return;
                }
            }
        }
        boolean playParticles = manageTreePhysics(event);
        if (!playParticles) return;
        if (!this.fancyPhysics.getPluginConfig().isBlockBreakingParticles()) return;
        if (fancyPhysics.getPluginConfig().isFlyUpParticles()) {
            this.fancyPhysics.getParticleGenerator().simulateBigBlockParticle(event.getBlock().getLocation(), event.getBlock().getType());
        } else {
            this.fancyPhysics.getParticleGenerator().simulateBlockParticles(event.getBlock());
        }
    }

    private boolean manageTreePhysics(BlockBreakEvent event) {
    if (isWood(event.getBlock().getRelative(BlockFace.UP).getType()) && this.fancyPhysics.getPluginConfig().isRealisticTrees()) {
        Tree tree;
        if (isWood(event.getBlock().getType())) {
            tree = new Tree(event.getBlock(), this.fancyPhysics);
        } else {
            tree = new Tree(event.getBlock().getRelative(BlockFace.UP), this.fancyPhysics);
            if (!tree.isNatural()) return false;
            if (!fancyPhysics.getPluginConfig().isGravityInAir()) return false;
            tree.getStem().forEach(block -> replaceWithFallingBlock(block, tree.getOrigin()));
            tree.getLeaves().forEach(block -> replaceWithFallingBlock(block, tree.getOrigin()));
            replaceWithFallingBlock(tree.getOrigin(), tree.getOrigin());
            return false;
        }

            if (fancyPhysics.getPluginConfig().isTreeChopDelay() && tree.isNatural() && tree.getStem().size() > 4 && !event.getBlock().getType().name().contains("STRIPPED") && !event.getBlock().getType().name().contains("MUD_BRICK_WALL") && !event.getBlock().getType().name().contains("FENCE")) {
                event.getBlock().setType(getStripedLog(event.getBlock().getType()));
                event.setCancelled(true);
                return false;
            }

            if (fancyPhysics.getPluginConfig().isTreeChopDelay() && tree.isNatural() && tree.getStem().size() > 8 && event.getBlock().getType().name().contains("STRIPPED")) {
                event.getBlock().setType(Material.MUD_BRICK_WALL);
                event.setCancelled(true);
                return false;
            }

            if (fancyPhysics.getPluginConfig().isTreeChopDelay() && tree.isNatural() && tree.getStem().size() > 8 && event.getBlock().getType().name().contains("MUD_BRICK_WALL")) {
                event.getBlock().setType(getFenceFromStrippedLog(event.getBlock().getType()));
                event.getBlock().getLocation().getWorld().spawn(event.getBlock().getLocation(), BlockDisplay.class, blockDisplay -> {
                    blockDisplay.setBlock(event.getBlock().getType().createBlockData());
                    blockDisplay.setInterpolationDuration(0);
                    blockDisplay.addScoreboardTag("fancyphysics_tree");
                    blockDisplay.setInterpolationDelay(-1);
                    blockDisplay.setTransformation(new Transformation(
                            new Vector3f(-1F, 0, -1F),
                            new Quaternionf(0, 0, 0, 1),
                            new Vector3f(2F, 1F, 2F),
                            new Quaternionf(0, 0, 0, 1)
                    ));
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
                        int animLength = 3;
                        blockDisplay.setInterpolationDuration(animLength);
                        blockDisplay.setInterpolationDelay(-1);
                        blockDisplay.setTransformation(new Transformation(
                                new Vector3f(0.1F, 0, 0.1F),
                                new Quaternionf(0, 0, 0, 1),
                                new Vector3f(0.9F, 0.9F, 0.9F),
                                new Quaternionf(0, 0, 0, 1)
                        ));
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, blockDisplay::remove, animLength);
                    }, 2L);
                });
                event.setCancelled(true);
                return true;
            }

            TreeBreakEvent treeBreakEvent = new TreeBreakEvent(tree);
        Bukkit.getServer().getPluginManager().callEvent(treeBreakEvent);
        if (treeBreakEvent.isCancelled()) return true;
        tree.breakWithFallAnimation(Optional.of(event.getPlayer()));
        if (fancyPhysics.getPluginConfig().isTreeRegeneration()) regenerate(tree, fancyPhysics.getPluginConfig().getTreeRegenerationDelay());
        
        // Prevent item drops for fences/mud walls
        Material brokenType = event.getBlock().getType();
        if (brokenType.name().endsWith("FENCE") || brokenType == Material.MUD_BRICK_WALL) {
            event.setDropItems(false); // Block breaks but no item drops
        }
        return true;
    }
    return true;
}

    private void replaceWithFallingBlock(Block block, Block origin) {
    final BlockData blockData = block.getType().createBlockData();
    if (block.getType() == Material.AIR) return;
    if (block != origin 
        && block.getRelative(BlockFace.DOWN).getType().isSolid() 
        && !isWood(block.getRelative(BlockFace.DOWN).getType())) { // Added closing ')'
        return;
    }

    Material material = block.getType();
    boolean isFenceOrMudWall = material.name().endsWith("FENCE") || material == Material.MUD_BRICK_WALL;

    // Remove potential item drops immediately
    if (isFenceOrMudWall) {
        block.setType(Material.AIR);
        block.getWorld().getNearbyEntities(block.getLocation(), 0.5, 0.5, 0.5).forEach(entity -> {
            if (entity instanceof org.bukkit.entity.Item item && item.getItemStack().getType() == material) {
                item.remove();
            }
        });
    } else {
        block.setType(Material.AIR);
    }
        
        block.getWorld().spawn(block.getLocation(), BlockDisplay.class, blockDisplay -> {
            blockDisplay.setBlock(blockData);
            blockDisplay.addScoreboardTag("fancyphysics_tree");
            Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
                Transformation transformation = new Transformation(
                    new Vector3f(0, -1.1F, 0),
                    blockDisplay.getTransformation().getLeftRotation(),
                    new Vector3f(1F, 1F, 1F),
                    blockDisplay.getTransformation().getRightRotation()
                );
                blockDisplay.setInterpolationDuration(5);
                blockDisplay.setInterpolationDelay(-1);
                blockDisplay.setTransformation(transformation);
                Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
                    Block targetBlock = block.getRelative(BlockFace.DOWN);
                    if (targetBlock.getType().isAir()) {
                        targetBlock.setType(blockData.getMaterial());
                    }
                    blockDisplay.remove();
                }, 5L);
            }, 2L);
        });
    }

    private void regenerate(Tree tree, int seconds) {
        Bukkit.getScheduler().runTaskLater(fancyPhysics, () -> {
            for (Location location : tree.getOldBlockList().keySet()) {
                var block = location.getBlock();
                if (block.getType() != Material.AIR) continue;
                block.setType(tree.getOldBlockList().get(location));
            }
            tree.getOrigin().setType(tree.getWood_material());
        }, 20L * seconds);
    }

    private Material getStripedLog(Material logType) {
        return switch (logType) {
            case BIRCH_LOG -> Material.STRIPPED_BIRCH_LOG;
            case SPRUCE_LOG -> Material.STRIPPED_SPRUCE_LOG;
            case DARK_OAK_LOG -> Material.STRIPPED_DARK_OAK_LOG;
            case ACACIA_LOG -> Material.STRIPPED_ACACIA_LOG;
            case JUNGLE_LOG -> Material.STRIPPED_JUNGLE_LOG;
            case CRIMSON_STEM -> Material.STRIPPED_CRIMSON_STEM;
            case WARPED_STEM -> Material.STRIPPED_WARPED_STEM;
            case MANGROVE_LOG -> Material.STRIPPED_MANGROVE_LOG;
            case CHERRY_LOG -> Material.STRIPPED_CHERRY_LOG;
            default -> Material.STRIPPED_OAK_LOG;
        };
    }

    private Material getFenceFromStrippedLog(Material strippedLog) {
        return switch (strippedLog) {
            case STRIPPED_BIRCH_LOG -> Material.BIRCH_FENCE;
            case STRIPPED_SPRUCE_LOG -> Material.SPRUCE_FENCE;
            case STRIPPED_DARK_OAK_LOG -> Material.DARK_OAK_FENCE;
            case STRIPPED_ACACIA_LOG -> Material.ACACIA_FENCE;
            case STRIPPED_JUNGLE_LOG -> Material.JUNGLE_FENCE;
            case STRIPPED_CRIMSON_STEM -> Material.CRIMSON_FENCE;
            case STRIPPED_WARPED_STEM -> Material.WARPED_FENCE;
            case STRIPPED_MANGROVE_LOG -> Material.MANGROVE_FENCE;
            case STRIPPED_CHERRY_LOG -> Material.CHERRY_FENCE;
            default -> Material.OAK_FENCE;
        };
    }

    private boolean isWood(Material pMaterial) {
        return pMaterial.name().endsWith("LOG") || pMaterial.name().endsWith("MUD_BRICK_WALL") || pMaterial.name().endsWith("STEM") || pMaterial.name().endsWith("FENCE");
    }
}
