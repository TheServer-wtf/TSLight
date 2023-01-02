package wtf.TheServer.TSLight.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import wtf.TheServer.TSLight.TSLightPlugin;
import wtf.TheServer.TSLight.controller.LightBlock;
import wtf.TheServer.TSLight.controller.LightController;
import wtf.TheServer.TSLight.controller.LightZone;
import wtf.TheServer.TSLight.inventory.LightMenuHolder;

public class PlayerListener implements Listener {
    private final TSLightPlugin plugin;

    public PlayerListener(TSLightPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        LightMenuHolder.disconnect(event.getPlayer());
        plugin.getLightMenuManager().exitBlockSetup(event.getPlayer());
        plugin.getLightMenuManager().exitZoneSetup(event.getPlayer());
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || !plugin.getLightMenuManager().inBlockSetup(player)){
            return;
        }
        Block block = event.getClickedBlock();
        if(block == null || block.isEmpty())
            return;
        LightBlock lightBlock;
        if((lightBlock = plugin.getLightMenuManager().getBlockSetup(player)) != null && lightBlock.getLocation() == null) {
            try {
                lightBlock.setLocation(block.getLocation());
                event.setCancelled(true);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(plugin.getCentral().getSystemPrefix() + "§cError: "+ex.getMessage());
                return;
            }
            plugin.getLightMenuManager().advanceSetup(player,0);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event){
        Player player = event.getPlayer();
        String msg = event.getMessage();
        if(!plugin.getLightMenuManager().inBlockSetup(player) && !plugin.getLightMenuManager().inZoneSetup(player))
            return;
        LightBlock lightBlock = plugin.getLightMenuManager().getBlockSetup(player);
        if(lightBlock != null && lightBlock.getLocation() == null)
            return;
        LightZone lightZone = plugin.getLightMenuManager().getZoneSetup(player);
        if(lightZone != null && (lightZone.getMin() == null || lightZone.getMax() == null))
            return;
        event.setCancelled(true);
        LightController controller = lightBlock != null ? lightBlock : lightZone;
        if((controller.getStart() < 0 || controller.getEnd() < 0)) {
            if(controller.getStart() > -1 && msg.equalsIgnoreCase("back")){
                if(lightBlock != null){
                    lightBlock.setStart(-1);
                } else {
                    lightZone.setStart(-1);
                }
                plugin.getLightMenuManager().advanceSetup(player,1);
                return;
            }
            DaylightCycle cycle = null;
            try {
                cycle = DaylightCycle.valueOf(msg.toUpperCase());
            } catch (IllegalArgumentException ignored){}
            long time = -1L;
            try {
                time = Long.parseLong(msg);
            } catch (NumberFormatException ignored){}
            if(cycle == null && time == -1L){
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cInvalid time given");
                return;
            }
            if(time == 24000)
                time = 0;
            if(controller.getStart() < 0){  // Stage #1
                if(cycle != null){
                    if(lightBlock != null){
                        lightBlock.setStart(cycle.getStart());
                    } else {
                        lightZone.setStart(cycle.getStart());
                    }
                } else {
                    if(time < 0 || time > 23999){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cThe given time is out of range (0-23999)");
                        return;
                    }
                    if(lightBlock != null){
                        lightBlock.setStart(time);
                    } else {
                        lightZone.setStart(time);
                    }
                }
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§aTurn off time set to §6"+controller.getStart());
                plugin.getLightMenuManager().advanceSetup(player,2);
            } else {    // Stage #2
                if(cycle != null){
                    if(controller.getStart() == cycle.getStart()){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cThe turn on time cannot be the same as the turn off time");
                        return;
                    }
                    if(lightBlock != null){
                        lightBlock.setEnd(cycle.getStart());
                    } else {
                        lightZone.setEnd(cycle.getStart());
                    }
                } else {
                    if(time < 0 || time > 23999){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cThe given time is out of range (0-23999)");
                        return;
                    }
                    if(controller.getStart() == time){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cThe turn on time cannot be the same as the turn off time");
                        return;
                    }
                    if(lightBlock != null){
                        lightBlock.setEnd(time);
                    } else {
                        lightZone.setEnd(time);
                    }
                }
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§aTurn on time set to §6"+controller.getEnd());
                plugin.getLightMenuManager().advanceSetup(player,3);
            }
            return;
        }
        if(controller.getLevel() < 1){
            if(msg.equalsIgnoreCase("back")){
                if(lightBlock != null){
                    lightBlock.setEnd(-1);
                } else {
                    lightZone.setEnd(-1);
                }
                plugin.getLightMenuManager().advanceSetup(player,2);
                return;
            }
            int level = -1;
            try {
                level = Integer.parseInt(msg);
            } catch (NumberFormatException ignored){}
            if(level < 1 || level > 15){
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cThe given level is out of range (1-15)");
                return;
            }
            if(lightBlock != null){
                lightBlock.setLevel(level);
            } else {
                lightZone.setLevel(level);
            }
            player.sendMessage(plugin.getCentral().getSystemPrefix()+"§aLight level set to §6"+controller.getLevel());
            plugin.getLightMenuManager().advanceSetup(player,4);
            return;
        }
        if(msg.equalsIgnoreCase("finish")){
            plugin.saveController(controller);
            plugin.getLightMenuManager().exitZoneSetup(player);
            plugin.getLightMenuManager().exitBlockSetup(player);
            player.sendMessage(plugin.getCentral().getSystemPrefix()+"§bThe light setup is now finished!");
        }
        if(msg.equalsIgnoreCase("back")){
            if(lightBlock != null){
                lightBlock.setLevel(-1);
            } else {
                lightZone.setLevel(-1);
            }
            plugin.getLightMenuManager().advanceSetup(player,3);
        }
    }
}
