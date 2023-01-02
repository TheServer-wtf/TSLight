package wtf.TheServer.TSLight;

import wtf.TheServer.TSLight.event.DaylightCycle;

import java.util.HashMap;
import java.util.UUID;

public class TSLightWorld {
    private static final HashMap<UUID, TSLightWorld> lightWorldMap = new HashMap<>();
    private final UUID world;
    private DaylightCycle daylightCycle = null;

    private TSLightWorld(UUID world) {
        this.world = world;
    }

    public static TSLightWorld getLightWorld(UUID world){
        if(!lightWorldMap.containsKey(world))
            lightWorldMap.put(world,new TSLightWorld(world));
        return lightWorldMap.get(world);
    }

    public UUID getWorld() {
        return world;
    }

    public DaylightCycle getDaylightCycle() {
        return daylightCycle;
    }

    public void setDaylightCycle(DaylightCycle daylightCycle) {
        this.daylightCycle = daylightCycle;
    }
}
