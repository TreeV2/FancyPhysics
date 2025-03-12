package com.maximde.fancyphysics.utils;

import com.maximde.fancyphysics.FancyPhysics;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class Tree {
    @Getter
    private boolean isNatural;
    @Getter
    private final Block origin;
    @Getter
    private final Material wood_material;
    private final Material leave_material;
    private final ArrayList<Block> stem = new ArrayList<>();
    private final ArrayList<Block> leaves = new ArrayList<>();
    private final FancyPhysics fancyPhysics;
    @Getter
    final HashMap<Location, Material> oldBlockList = new HashMap<Location, Material>();

    public Tree(Block origin, FancyPhysics fancyPhysics) {
        this.fancyPhysics = fancyPhysics;
        this.origin = origin;
        Block aboveOrigin = origin.getLocation().clone().add(0, 1, 0).getBlock();
        this.wood_material = aboveOrigin.getType();
        this.leave_material = Material.valueOf(getLeaveType(this.wood_material));
        scanTree(aboveOrigin);
        stem.add(origin);
        this.isNatural = (this.stem.size() > 3 && this.leaves.size() > 5);
    }

    public void breakWithFallAnimation(Optional<Player> player) {
        player.ifPresent(value -> {
            if(fancyPhysics.getPluginConfig().isAffectedBlocksInPlayerStats()) {
                if(this.stem.size() > 0)  value.incrementStatistic(Statistic.MINE_BLOCK, this.wood_material, this.stem.size());
                if(this.leaves.size() > 0) value.incrementStatistic(Statistic.MINE_BLOCK, this.leave_material, this.leaves.size());
            }
    
            // Decrease tool durability only for additional stem blocks (excluding origin and leaves)
            ItemStack tool = value.getInventory().getItemInMainHand();
            if (tool == null || tool.getType() == Material.AIR) {
                return;
            }
    
            ItemMeta meta = tool.getItemMeta();
            if (!(meta instanceof Damageable)) {
                return;
            }
    
            Damageable damageable = (Damageable) meta;
            int totalDamage = this.stem.size() - 1; // Exclude origin block
    
            if (totalDamage <= 0) return; // No additional blocks to charge
    
            int newDamage = damageable.getDamage() + totalDamage;
            int maxDurability = tool.getType().getMaxDurability();
    
            // Ensure tool doesn't get destroyed if at exactly max durability
            if (newDamage >= maxDurability) {
                value.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                damageable.setDamage(newDamage);
                tool.setItemMeta(meta);
                value.getInventory().setItemInMainHand(tool); // Update durability
            }
        });
    
        // Rest of the method remains unchanged
        if(!isNatural) return;
        for (Block b : this.leaves) spawnDisplay(b);
        for (Block b : this.stem)  spawnDisplay(b);
        if(fancyPhysics.getPluginConfig().isSounds()) {
            origin.getLocation().getWorld().playSound(origin.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1.0f, 1.0f);
            Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
                origin.getWorld().playSound(origin.getLocation(), Sound.ENTITY_ARMOR_STAND_PLACE, 1.0f, 1.0f);
            }, 18L);
        }
    }

    private void spawnDisplay(Block block) {
    final var location = block.getLocation();
    final BlockData blockData = block.getType().createBlockData(); // Now effectively final

    location.getWorld().spawn(location, BlockDisplay.class, blockDisplay -> {
        this.fancyPhysics.displayList.add(blockDisplay);
        blockDisplay.setBlock(blockData);
        blockDisplay.addScoreboardTag("fancyphysics_tree");
        if (block != origin) block.setType(Material.AIR);

        var transformationY = -1 + (this.origin.getY() - (block.getY()));
        var transformationZ = (this.origin.getY() - block.getY()) + (this.origin.getY() - block.getY()) / 0.9F;

            Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
            final var loc = blockDisplay.getLocation().add(0, (this.origin.getY() - (block.getY() + 0.7F)) + 1.5F, transformationY - 0.5F);

            // Fix 1: Make impactLocation effectively final
            Block impactLocation = loc.getBlock();
            if (impactLocation.getType().isSolid()) {
                int tries = 0;
                while (impactLocation.getType().isSolid() && tries < 5) {
                    impactLocation = impactLocation.getRelative(BlockFace.UP);
                    tries++;
                }
            }

            // Create a final copy for use in the lambda
            final Block finalImpactLocation = impactLocation;

            Transformation transformation = new Transformation(
                    new Vector3f(0, transformationY + (this.origin.getY() - (block.getY() + 0.6F)) / 2, transformationZ),
                    new Quaternionf(-1.0F + (float) loc.distance(finalImpactLocation.getLocation()) / 10, 0, 0, 0.1),
                    new Vector3f(1F, 1F, 1F),
                    blockDisplay.getTransformation().getRightRotation()
            );
            blockDisplay.setInterpolationDuration(30);
            blockDisplay.setInterpolationDelay(-1);
            blockDisplay.setTransformation(transformation);

            // Fix 2: Use finalImpactLocation and blockData (already effectively final)
            Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
                if (!finalImpactLocation.getType().isSolid()) {
                    this.fancyPhysics.getParticleGenerator().simulateBlockParticles(
                            finalImpactLocation.getLocation(), 
                            blockData.getMaterial() // blockData is effectively final
                    );
                }
                removeTree(blockDisplay, transformationY, blockData);
            }, 12L - Math.min(11, (int) (loc.distance(finalImpactLocation.getLocation()) * 2)));
        }, 2L);
    });
}

    private void removeTree(BlockDisplay blockDisplay, float transformationY, BlockData blockData) {
    Bukkit.getScheduler().scheduleSyncDelayedTask(this.fancyPhysics, () -> {
        Material material = blockData.getMaterial();
        if (this.fancyPhysics.getPluginConfig().isDropSaplings()) {
            var b = blockDisplay.getLocation().add(0, transformationY + 2, transformationY).getBlock();
            if (b.getType() == Material.AIR) {
                // Do not place or break fences/mud walls
                if (material != Material.MUD_BRICK_WALL && !material.name().endsWith("FENCE")) {
                    b.setType(material);
                    b.breakNaturally();
                }
            }
        } else {
            // Skip dropping for fences/mud walls
            if (material != Material.MUD_BRICK_WALL && !material.name().endsWith("FENCE")) {
                blockDisplay.getLocation().getWorld().dropItem(
                        blockDisplay.getLocation().add(0, transformationY + 2, transformationY),
                        new ItemStack(material)
                );
            }
        }
        this.fancyPhysics.displayList.remove(blockDisplay);
        blockDisplay.remove();
    }, 4L);
}

    public void breakInstant() {
        if (!isNatural) return;
        for (Block b : this.stem)
            b.breakNaturally();
        for (Block b : this.leaves)
            b.breakNaturally();
    }

    public void breakInstantWithParticles() {
        if (!isNatural) return;
        for (Block b : this.stem) {
            this.fancyPhysics.getParticleGenerator().simulateBlockParticles(b);
            b.breakNaturally();
        }
        for (Block b : this.leaves) {
            this.fancyPhysics.getParticleGenerator().simulateBlockParticles(b);
            b.breakNaturally();
        }
    }

    private String getLeaveType(Material material) {
        return switch (material.name()) {
            case "OAK_LOG", "MUD_BRICK_WALL", "STRIPPED_OAK_LOG", "OAK_FENCE" -> "OAK_LEAVES";
            case "DARK_OAK_LOG", "STRIPPED_DARK_OAK_LOG", "DARK_OAK_FENCE" -> "DARK_OAK_LEAVES";
            case "JUNGLE_LOG", "STRIPPED_JUNGLE_LOG", "JUNGLE_FENCE" -> "JUNGLE_LEAVES";
            case "ACACIA_LOG", "STRIPPED_ACACIA_LOG", "ACACIA_FENCE" -> "ACACIA_LEAVES";
            case "BIRCH_LOG", "STRIPPED_BIRCH_LOG", "BIRCH_FENCE" -> "BIRCH_LEAVES";
            case "SPRUCE_LOG", "STRIPPED_SPRUCE_LOG", "SPRUCE_FENCE" -> "SPRUCE_LEAVES";
            case "CHERRY_LOG", "STRIPPED_CHERRY_LOG", "CHERRY_FENCE" -> "CHERRY_LEAVES";
            case "MANGROVE_LOG", "STRIPPED_MANGROVE_LOG", "MANGROVE_FENCE" -> "MANGROVE_LEAVES";
            case "WARPED_STEM", "NETHER_WART_BLOCK" -> "WARPED_WART_BLOCK";
            case "CRIMSON_STEM" -> "NETHER_WART_BLOCK";
            default -> "AIR";
        };
    }

    private int distanceToLastValid = 0;
    private int amount = 0;
    private List<Block> scannedBlocks = new ArrayList<>();

    private void scanTree(Block block) {
        scannedBlocks.add(block);
        amount++;
        if (Math.abs(block.getX() - this.origin.getX()) > 10 || Math.abs(block.getZ() - this.origin.getZ()) > 10)
            return;
        if (block.getType() == this.wood_material) {
            if (this.stem.size() < this.fancyPhysics.getPluginConfig().getTreeMaxStemSize()) {
                if (this.stem.contains(block))
                    return;
                this.stem.add(block);
                this.oldBlockList.put(block.getLocation(), block.getType());
            } else {
                this.isNatural = false;
                return;
            }
        } else if (block.getType() == this.leave_material) {
            if (this.leaves.size() < this.fancyPhysics.getPluginConfig().getTreeMaxLeavesSize()) {
                if (this.leaves.contains(block))
                    return;
                this.leaves.add(block);
                this.oldBlockList.put(block.getLocation(), block.getType());
            } else {
                this.isNatural = false;
                return;
            }
        }

        boolean advancedStemScan = this.fancyPhysics.getPluginConfig().isAdvancedStemScan();

        if (Arrays.asList(Material.COCOA_BEANS, Material.VINE, Material.SNOW).contains(block.getType()) && advancedStemScan) {
            block.breakNaturally();
        }

        Arrays.asList(BlockFace.DOWN, BlockFace.UP, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST).forEach(blockFace -> {
            final var currentBlock = block.getRelative(blockFace);

            boolean scan = (currentBlock.getType() == this.wood_material || currentBlock.getType() == this.leave_material);
            if (blockFace == BlockFace.DOWN && currentBlock.getY() <= this.origin.getY() + 12) {
                scan = false;
            }
            if (scan) {
                scanTree(currentBlock);
                distanceToLastValid = 0;
                return;
            }

            if (amount < this.fancyPhysics.getPluginConfig().getTreeMaxInvalidScans() && this.stem.size() > 4 && advancedStemScan && distanceToLastValid < this.fancyPhysics.getPluginConfig().getTreeMaxInvalidBlockDistance()) {
                distanceToLastValid++;
                if (!scannedBlocks.contains(currentBlock)) scanTree(currentBlock);
            }
        });
    }

    public ArrayList<Block> getStem() {
        return stem;
    }

    public ArrayList<Block> getLeaves() {
        return leaves;
    }
}
