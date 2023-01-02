package wtf.TheServer.TSLight.inventory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.TheServer.TSLight.TSLightPlugin;
import wtf.TheServer.TSLight.controller.LightBlock;
import wtf.TheServer.TSLight.controller.LightController;
import wtf.TheServer.TSLight.controller.LightZone;
import wtf.TheServer.TSLight.event.DaylightCycle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static wtf.TheServer.TSLight.TSLightPlugin.formatLocation;

public class LightMenuManager implements Listener {
    private final TSLightPlugin plugin;
    private final NamespacedKey CREATE_KEY;
    private final NamespacedKey LIGHT_KEY;

    public LightMenuManager(TSLightPlugin plugin) {
        this.plugin = plugin;
        CREATE_KEY = new NamespacedKey(plugin,"create");
        LIGHT_KEY = new NamespacedKey(plugin,"light");
    }

    public void openLightList(@NotNull Player target){
        if(inZoneSetup(target) || inBlockSetup(target)){
            target.sendMessage(plugin.getCentral().getSystemPrefix()+"§cYou have a setup in progress!");
            return;
        }
        Set<LightController> controllers = new HashSet<>();
        controllers.addAll(plugin.getLightBlocks());
        controllers.addAll(plugin.getLightZones());
        boolean noItems = controllers.size() == 0;

        LightMenuHolder menuHolder = new LightMenuHolder(target,noItems ? 27 : 54, "TSLight Menu");

        if(noItems) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§cNo zones or blocks");
            meta.setLore(List.of(
                    "§7There are no light zones or blocks",
                    "§7created yet. Check back later or",
                    "§7create a new one!"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.addEnchant(Enchantment.DURABILITY, 5, true);
            item.setItemMeta(meta);
            menuHolder.getInventory().setItem(13, item);
        }
        AtomicInteger items = new AtomicInteger(0);
        plugin.getServer().getScheduler().scheduleAsyncDelayedTask(plugin,()->{
            try {
                controllers.forEach(light -> {
                    Block block = light.getBlocks().stream().findFirst().orElse(null);
                    if (block == null)
                        return;
                    ItemStack item = new ItemStack(block.getType().isItem() ? block.getType() : Material.STRING);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName("§e§l" + light.getId().toString().split(Pattern.quote("-"))[0]);
                    meta.setLore(List.of(
                            "§7" + formatLocation(light.getLocation()),
                            "§f",
                            "§cTurn off: " + light.getStart(),
                            "§aTurn on: " + light.getEnd(),
                            "§fLight level: " + light.getLevel(),
                            "§f",
                            "§4Press §5Shift+Right click§4 to remove"
                    ));
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(LIGHT_KEY, PersistentDataType.STRING, light.getId().toString());
                    item.setItemMeta(meta);
                    menuHolder.getInventory().setItem(items.getAndIncrement(), item);
                    menuHolder.getInventory().getViewers().forEach(v -> ((Player) v).updateInventory());
                    if(items.get() == 45)
                        throw new RuntimeException();   // This is the worst solution possible according to StackOverflow
                                                        // I'll fix this later so there is a pagination
                });
            } catch (Exception ignored){}
        });

        if(target.hasPermission("tslight.edit")) {
            if(plugin.isDebug()) plugin.getLogger().info("LightMenuManager: Target has permission to edit");
            // Create zone or block
            ItemStack item = new ItemStack(Material.LIME_CANDLE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§aNew zone");
            meta.setLore(List.of(
                    "§7Click here to create",
                    "§7a new light zone"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(CREATE_KEY, PersistentDataType.STRING, "zone");
            item.setItemMeta(meta);
            menuHolder.getInventory().setItem(noItems ? 18 : 45, item);

            item = new ItemStack(Material.GREEN_CANDLE);
            meta = item.getItemMeta();
            meta.setDisplayName("§aNew block");
            meta.setLore(List.of(
                    "§7Click here to create",
                    "§7a single light block"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            pdc = meta.getPersistentDataContainer();
            pdc.set(CREATE_KEY, PersistentDataType.STRING, "block");
            item.setItemMeta(meta);
            menuHolder.getInventory().setItem(noItems ? 19 : 46, item);
        }

        target.openInventory(menuHolder.getInventory());
    }

    private final Map<UUID, LightBlock> blockSetup = new ConcurrentHashMap<>();
    private final Map<UUID, LightZone> zoneSetup = new ConcurrentHashMap<>();

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event){
        Inventory inventory = event.getInventory();
        if(inventory.getHolder() instanceof LightMenuHolder){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        Inventory inventory = event.getInventory();
        if(inventory.getHolder() instanceof LightMenuHolder){
            Player player = (Player) event.getWhoClicked();
            ItemStack item = event.getCurrentItem();
            event.setCancelled(true);
            if(item != null){
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if(event.getClick().equals(ClickType.SHIFT_RIGHT) && pdc.has(LIGHT_KEY,PersistentDataType.STRING)){
                    plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
                    String sid = pdc.get(LIGHT_KEY,PersistentDataType.STRING);
                    UUID uid = UUID.fromString(sid);
                    boolean remove = plugin.removeController(uid);
                    if(remove){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cLight controller with ID '§e§l"+sid.split(Pattern.quote("-"))[0]+"§c' has been removed.");
                    } else {
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§cFailed to remove light controller with ID '§5"+sid.split(Pattern.quote("-"))[0]+"§c'");
                    }
                }
                if(pdc.has(CREATE_KEY,PersistentDataType.STRING)){
                    if(inBlockSetup(player) || inZoneSetup(player)){
                        return; // How did we get here ???
                    }
                    String type = pdc.get(CREATE_KEY,PersistentDataType.STRING);
                    player.closeInventory();
                    if(plugin.isDebug()) plugin.getLogger().info("LightMenuManager: "+type.toUpperCase()+" setup initiated by "+player.getName());
                    if(type.equalsIgnoreCase("block")){
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§bWelcome to the light block setup!");
                        player.sendMessage("§eRight click on a light emitting block to mark it as a light block.");
                        player.sendMessage("§aValid light emitting blocks can be viewed with §d/tslight blocks");
                        player.sendMessage("§cType in §d/tslight cancel§c at any point to exit the setup");
                        blockSetup.put(player.getUniqueId(), new LightBlock());
                    } else {
                        player.sendMessage(plugin.getCentral().getSystemPrefix()+"§bWelcome to the light zone setup!");
                        player.sendMessage("§eMake a selection with WorldEdit, then type in §d/tslight done§e to proceed to the next step.");
                        player.sendMessage("§cType in §d/tslight cancel§c at any point to exit the setup");
                        zoneSetup.put(player.getUniqueId(), new LightZone());
                    }
                }
            }
        }
    }

    /**
     * @param player the player to check for
     * @return true if the player has an active block setup
     */
    public boolean inBlockSetup(@NotNull Player player){
        return blockSetup.containsKey(player.getUniqueId());
    }

    /**
     * @param player the target player
     * @return {@link LightBlock} currently in setup or {@code null}
     * @see #inBlockSetup(Player)
     */
    @Nullable
    public LightBlock getBlockSetup(@NotNull Player player){
        return blockSetup.get(player.getUniqueId());
    }

    /**
     * @param player the player to check for
     * @return true if the player had an active block setup
     */
    public boolean exitBlockSetup(@NotNull Player player){
        return blockSetup.remove(player.getUniqueId()) != null;
    }

    /**
     * @param player the player to check for
     * @return true if the player has an active zone setup
     */
    public boolean inZoneSetup(@NotNull Player player){
        return zoneSetup.containsKey(player.getUniqueId());
    }

    /**
     * @param player the target Player
     * @return {@link LightZone} currently in setup or {@code null}
     * @see #inZoneSetup(Player)
     */
    @Nullable
    public LightZone getZoneSetup(@NotNull Player player){
        return zoneSetup.get(player.getUniqueId());
    }

    /**
     * @param player the player to check for
     * @return true if the player had an active zone setup
     */
    public boolean exitZoneSetup(@NotNull Player player){
        return zoneSetup.remove(player.getUniqueId()) != null;
    }

    public void advanceSetup(@NotNull Player player, int stage){
        if(!inBlockSetup(player) && !inZoneSetup(player))
            return;
        switch (stage){
            // Stage 0 is technically the block or worldedit selection but we don't handle that here
            case 0, 1 -> {
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§ePlease specify the time the lights turn off (0-23999)");
                player.sendMessage("§aYou can also use one of the following: §d"+ Arrays.stream(DaylightCycle.values()).map(Enum::toString).map(String::toLowerCase).collect(Collectors.joining(", ")));
            }
            case 2 -> {
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§ePlease specify the time the lights turn back on (0-23999)");
                player.sendMessage("§6Please make sure this value differs from the turn off time");
            }
            case 3 -> {
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§ePlease provide the level a Light entity should glow at (1-15)");
                player.sendMessage("§6This should be set even if you don't currently have a Light entity");
            }
            case 4 -> {
                String type;
                player.sendMessage(plugin.getCentral().getSystemPrefix()+"§eYou are almost finished! Please verify that the information below is correct.");
                if(inBlockSetup(player)){
                    type = "block";
                    LightBlock block = getBlockSetup(player);
                    Location loc = block.getLocation();
                    player.sendMessage("§6Block location: §d"+formatLocation(loc));
                    player.sendMessage("§6Turn §coff §6time: §d"+block.getStart());
                    player.sendMessage("§6Turn §aon §6time: §d"+block.getEnd());
                    player.sendMessage("§6Light level: §d"+block.getLevel());
                } else {
                    type = "zone";
                    LightZone zone = getZoneSetup(player);
                    Location min = zone.getMin();
                    Location max = zone.getMax();
                    player.sendMessage("§6Zone location: §d"+formatLocation(min)+" §6-> §d"+formatLocation(max));
                    player.sendMessage("§6Turn §coff §6time: §d"+zone.getStart());
                    player.sendMessage("§6Turn §aon §6time: §d"+zone.getEnd());
                    player.sendMessage("§6Light level: §d"+zone.getLevel());
                    player.sendMessage("§6Light count: §d"+zone.getBlocks().size());
                    player.sendMessage("§7This light count isn't final, you can change it anytime, it's only here for you to check if you defined the zone correctly");
                }
                player.sendMessage("");
                player.sendMessage("§5If you provided a wrong value, you can go back by typing in§d back§5");
                player.sendMessage("§eIf everything is in order, type in§d finish§e to save this "+type+" and exit the setup.");
            }
            default -> {
                // invalid setup state lmao
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event){
        if(event.getInventory().getHolder() instanceof LightMenuHolder holder){
            holder.close();
        }
    }
}
