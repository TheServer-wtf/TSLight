package wtf.TheServer.TSLight;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.TheServer.TSLight.controller.LightBlock;
import wtf.TheServer.TSLight.controller.LightZone;

import java.util.ArrayList;
import java.util.List;

public class TSLightCommand implements TabExecutor {
    private final TSLightPlugin plugin;

    public TSLightCommand(TSLightPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!sender.hasPermission("tslight.use")){
            return true;
        }
        if(args.length > 0){
            switch (args[0].toLowerCase()) {
                case "debug" -> {
                    plugin.getLogger().info("TSLightCommand: Plugin debug state change requested");
                    plugin.setDebug(!plugin.isDebug());
                    sender.sendMessage(plugin.getCentral().getSystemPrefix() + "Plugin debug " + (plugin.isDebug() ? "enabled" : "disabled"));
                }
                case "blocks" -> {
                    if(sender.hasPermission("tslight.edit"))
                        sender.sendMessage(plugin.getCentral().getSystemPrefix() + "§eValid light blocks:§d" + "\n" + LightBlock.validBlocks());
                }
                case "cancel" -> {
                    if(sender instanceof Player player && player.hasPermission("tslight.edit")){
                        boolean block = plugin.getLightMenuManager().exitBlockSetup(player);
                        boolean zone = plugin.getLightMenuManager().exitZoneSetup(player);
                        if(!block && !zone){
                            player.sendMessage(plugin.getCentral().getSystemPrefix() + "§cYou don't have an active setup.");
                        } else {
                            player.sendMessage(plugin.getCentral().getSystemPrefix() + "§cLight "+(block ? "block" : "zone")+" setup cancelled.");
                        }
                    }
                }
                case "done" -> {
                    if(sender instanceof Player player && plugin.getLightMenuManager().inZoneSetup(player)){
                        LightZone lightZone = plugin.getLightMenuManager().getZoneSetup(player);
                        if(lightZone.getMin() == null || lightZone.getMax() == null) {
                            com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
                            SessionManager manager = WorldEdit.getInstance().getSessionManager();
                            LocalSession session = manager.get(actor);
                            Region region;
                            World bWorld = player.getWorld();
                            com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bWorld);
                            try {
                                region = session.getSelection(world);
                                BlockVector3 minVector = region.getMinimumPoint();
                                Location minLoc = new Location(bWorld, minVector.getBlockX(), minVector.getBlockY(), minVector.getBlockZ());
                                BlockVector3 maxVector = region.getMaximumPoint();
                                Location maxLoc = new Location(bWorld, maxVector.getBlockX(), maxVector.getBlockY(), maxVector.getBlockZ());
                                Vector3 centerVector = region.getCenter();
                                Location centerLoc = new Location(bWorld,centerVector.getX(),centerVector.getY(),centerVector.getZ());
                                lightZone.setZone(minLoc,maxLoc);
                                lightZone.setCenter(centerLoc);
                            } catch (IncompleteRegionException ex) {
                                player.sendMessage(plugin.getCentral().getSystemPrefix() + "§cYou have no WorldEdit selection in this world.");
                                break;
                            } catch (IllegalArgumentException | IllegalStateException ex) {
                                player.sendMessage(plugin.getCentral().getSystemPrefix() + "§cError: " + ex.getMessage());
                                break;
                            }
                            plugin.getLightMenuManager().advanceSetup(player,0);
                        }
                    }
                }
                default -> sender.sendMessage(plugin.getCentral().getSystemPrefix() + "§cInvalid argument.");
            }
            return true;
        }
        if(!(sender instanceof Player player)){
            return false;
        }
        if(plugin.isDebug()) plugin.getLogger().info("TSLightCommand: "+player.getName()+" passed the vibe check, opening menu...");
        plugin.getLightMenuManager().openLightList(player);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        ArrayList<String> tabs = new ArrayList<>();
        if(!sender.hasPermission("tslight.use")) {
            return tabs;
        }
        tabs.add("debug");
        if(!sender.hasPermission("tslight.edit")){
            return tabs;
        }
        tabs.add("blocks");
        if(sender instanceof Player player){
            boolean zone = plugin.getLightMenuManager().inZoneSetup(player);
            if(!zone && !plugin.getLightMenuManager().inBlockSetup(player)){
                return tabs;
            }
            tabs.add("cancel");
            if(!zone){
                return tabs;
            }
            tabs.add("done");
        }
        return tabs;
    }
}
