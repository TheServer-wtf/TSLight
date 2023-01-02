package wtf.TheServer.TSLight.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LightMenuHolder implements InventoryHolder {
    private static final Map<UUID,Inventory> invMap = new ConcurrentHashMap<>();
    private final UUID player;
    private final UUID id;

    public LightMenuHolder(@NotNull Player player, int size, @NotNull String title) {
        if(size % 9 != 0){
            throw new IllegalArgumentException("Size must be a multiple of 9");
        }
        if(size > 54){
            throw new IllegalArgumentException("Size can not exceed 54");
        }
        this.player = player.getUniqueId();
        this.id = UUID.randomUUID();
        Inventory inventory = invMap.getOrDefault(getId(), Bukkit.createInventory(this, size, title));
        if(!invMap.containsKey(getId())){
            invMap.put(getId(),inventory);
        }
    }

    public LightMenuHolder(@NotNull Player player, @NotNull InventoryType type, @NotNull String title) {
        this.player = player.getUniqueId();
        this.id = UUID.randomUUID();
        Inventory inventory = invMap.getOrDefault(getId(), Bukkit.createInventory(this, type, title));
        if(!invMap.containsKey(getId())){
            invMap.put(getId(),inventory);
        }
    }

    @NotNull
    public UUID getId() {
        return id;
    }

    @NotNull
    public UUID getPlayer() {
        return player;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return invMap.get(getId());
    }

    public void close() {
        if(!invMap.containsKey(getId()))
            return;
        invMap.remove(getId());
    }

    public static void disconnect(@NotNull Player player){
        invMap.entrySet().stream()
                .filter(e -> ((LightMenuHolder) e.getValue().getHolder()).getPlayer().equals(player.getUniqueId()))
                .forEach(e->invMap.remove(e.getKey()));
    }
}
