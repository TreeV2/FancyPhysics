package com.maximde.fancyphysics.listeners;

import com.maximde.fancyphysics.FancyPhysics;
import com.maximde.fancyphysics.components.ParticleDisplay;
import com.maximde.fancyphysics.utils.Config;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockBreakListener implements Listener {
    private FancyPhysics fancyPhysics;
    public BlockBreakListener(FancyPhysics fancyPhysics) {
        this.fancyPhysics = fancyPhysics;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        var block = event.getBlock();
        if(event.isCancelled()) return;
        if(!fancyPhysics.config.isBlockParticles()) return;
        this.fancyPhysics.utils.simulateBlockParticles(block);
    }

}
