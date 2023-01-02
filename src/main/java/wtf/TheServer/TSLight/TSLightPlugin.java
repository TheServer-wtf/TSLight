package wtf.TheServer.TSLight;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.TheServer.TSCPlugin;
import wtf.TheServer.TSLight.controller.LightBlock;
import wtf.TheServer.TSLight.controller.LightController;
import wtf.TheServer.TSLight.controller.LightZone;
import wtf.TheServer.TSLight.event.DaylightCycle;
import wtf.TheServer.TSLight.event.DaylightCycleChangeEvent;
import wtf.TheServer.TSLight.event.PlayerListener;
import wtf.TheServer.TSLight.inventory.LightMenuManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class TSLightPlugin extends JavaPlugin implements TSLight {
    private TSCPlugin central;
    private LightMenuManager lightMenuManager;
    private final Set<LightBlock> lightBlocks = new HashSet<>();
    private final Set<LightZone> lightZones = new HashSet<>();
    private boolean debug = false;

    @Override
    public void onEnable() {
        central = (TSCPlugin) getServer().getPluginManager().getPlugin("TSCentralPlugin");
        File lights = new File(getDataFolder(),"/lights/");
        File[] files = lights.listFiles((dir, name) -> name.endsWith(".yml"));
        if(files != null) {
            Arrays.stream(files).forEach(file -> {
                try {
                    loadController(file);
                } catch (FileNotFoundException | NullPointerException | IllegalArgumentException e) {
                    getLogger().warning("Failed to load light controller file '"+file.getPath()+"': "+e);
                }
            });
        }
        getLogger().info("Loaded "+(lightBlocks.size()+lightZones.size())+" light controller(s)");
        startDaylightCycleTask();
        startLightBlockTask();
        startLightZoneTask();
        lightMenuManager = new LightMenuManager(this);
        getServer().getPluginManager().registerEvents(lightMenuManager,this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this),this);
        getCommand("tslight").setExecutor(new TSLightCommand(this));
        getLogger().info("The plugin is now enabled and ready for action");
    }

    private void startLightZoneTask() {
        getServer().getScheduler().scheduleAsyncRepeatingTask(this,()->{
            lightZones.forEach(light->{
                World w = light.getMin().getWorld();
                boolean state = light.getState();
                int start = (int) (Math.min(light.getStart(), light.getEnd()));
                int end = (int) (Math.max(light.getStart(), light.getEnd()));
                boolean turnOn = IntStream.range(start, end).anyMatch(r->r==w.getTime());
                if(!turnOn){
                    if(state) {
                        if(isDebug()) getLogger().info("LightZoneTask: "+light.getId()+" is turning off");
                        light.setState(false);
                        light.getBlocks().forEach(block -> {
                            getServer().getScheduler().runTask(this,()->{
                                if (block.getType().equals(Material.LIGHT)) {
                                    Levelled levelled = (Levelled) block.getBlockData();
                                    levelled.setLevel(0);
                                    block.setBlockData(levelled);
                                } else {
                                    Lightable lightable = (Lightable) block.getBlockData();
                                    lightable.setLit(false);
                                    block.setBlockData(lightable);
                                }
                            });
                        });
                    }
                } else {
                    if(!state){
                        if(isDebug()) getLogger().info("LightZoneTask: "+light.getId()+" is turning on");
                        light.setState(true);
                        light.getBlocks().forEach(block -> {
                            getServer().getScheduler().runTask(this,()->{
                                if (block.getType().equals(Material.LIGHT)) {
                                    Levelled levelled = (Levelled) block.getBlockData();
                                    levelled.setLevel(light.getLevel());
                                    block.setBlockData(levelled);
                                } else {
                                    Lightable lightable = (Lightable) block.getBlockData();
                                    lightable.setLit(true);
                                    block.setBlockData(lightable);
                                }
                            });
                        });
                    }
                }
            });
        },1,1);
    }

    private void startLightBlockTask() {
        getServer().getScheduler().scheduleAsyncRepeatingTask(this,()->{
            lightBlocks.forEach(light->{
                World w = light.getLocation().getWorld();
                Block block = light.getBlocks().stream().findFirst().orElse(null);
                if(block == null)
                    return;
                boolean state = light.getState();
                int start = (int) (Math.min(light.getStart(), light.getEnd()));
                int end = (int) (Math.max(light.getStart(), light.getEnd()));
                boolean turnOn = IntStream.range(start, end).anyMatch(r->r==w.getTime());
                if(!turnOn){
                    if(state) {
                        if(isDebug()) getLogger().info("LightBlockTask: "+light.getId()+" is turning off");
                        light.setState(false);
                        getServer().getScheduler().runTask(this,()->{
                            if (block.getType().equals(Material.LIGHT)) {
                                Levelled levelled = (Levelled) block.getBlockData();
                                levelled.setLevel(0);
                                block.setBlockData(levelled);
                            } else {
                                Lightable lightable = (Lightable) block.getBlockData();
                                lightable.setLit(false);
                                block.setBlockData(lightable);
                            }
                        });
                    }
                } else {
                    if(!state){
                        if(isDebug()) getLogger().info("LightBlockTask: "+light.getId()+" is turning on");
                        light.setState(true);
                        getServer().getScheduler().runTask(this,()->{
                            if (block.getType().equals(Material.LIGHT)) {
                                Levelled levelled = (Levelled) block.getBlockData();
                                levelled.setLevel(light.getLevel());
                                block.setBlockData(levelled);
                            } else {
                                Lightable lightable = (Lightable) block.getBlockData();
                                lightable.setLit(true);
                                block.setBlockData(lightable);
                            }
                        });
                    }
                }
            });
        },1,1);
    }

    private void startDaylightCycleTask() {
        getServer().getScheduler().scheduleAsyncRepeatingTask(this,()->{
            getServer().getWorlds().stream().filter(w->
                    // Checking for normal or custom worlds only, because (as you may or may not know) the nether and the end doesn't have a daylight cycle, but in the code time still progresses
                    w.getEnvironment().equals(World.Environment.NORMAL) || w.getEnvironment().equals(World.Environment.CUSTOM)
            ).forEach(w->{
                TSLightWorld lw = TSLightWorld.getLightWorld(w.getUID());
                DaylightCycle daylightCycle = lw.getDaylightCycle();
                DaylightCycle dlc = DaylightCycle.getCycle(w.getTime());
                if(dlc == null)
                    return;
                lw.setDaylightCycle(dlc);
                if(daylightCycle != null && daylightCycle != dlc){
                    getServer().getScheduler().runTask(this,()->changeCycle(w,dlc));
                }
            });
        },0,1);
    }

    public TSCPlugin getCentral() {
        return central;
    }

    public LightMenuManager getLightMenuManager() {
        return lightMenuManager;
    }

    private void changeCycle(World world, DaylightCycle daylightCycle){
        // We don't really need to do this, but we should help out other plugins in any way we can
        getServer().getPluginManager().callEvent(new DaylightCycleChangeEvent(world,daylightCycle));
        if(isDebug()) getLogger().info("DaylightCycleChangeEvent: "+world.getName()+"#"+daylightCycle);
    }

    public boolean isDebug() {
        return debug;
    }
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @NotNull
    public LightController loadController(@NotNull final File file)
            throws FileNotFoundException, NullPointerException, IllegalArgumentException
    {
        if(!file.exists()){
            throw new FileNotFoundException();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        UUID id = UUID.fromString(config.getString("id"));
        String type = config.getString("type");
        LightController controller;
        if(type.equals("block")) {
            String s_world = config.getString("location.world");
            World b_world = getServer().getWorld(s_world);
            Objects.requireNonNull(b_world,"Location world not loaded");
            int X = config.getInt("location.X");
            int Y = config.getInt("location.Y");
            int Z = config.getInt("location.Z");
            Location loc = new Location(b_world,X,Y,Z);

            LightBlock block = new LightBlock(id,loc);
            block.setStart(config.getInt("time.start"));
            block.setEnd(config.getInt("time.end"));
            block.setLevel(config.getInt("level",15));
            int start = (int) (Math.min(block.getStart(), block.getEnd()));
            int end = (int) (Math.max(block.getStart(), block.getEnd()));
            boolean turnOn = IntStream.range(start, end).anyMatch(r->r==block.getLocation().getWorld().getTime());
            block.setState(!turnOn);

            lightBlocks.add(block);

            controller = block;
        } else {
            String min_s_world = config.getString("location.min.world");
            World min_b_world = getServer().getWorld(min_s_world);
            Objects.requireNonNull(min_b_world,"Min location world not loaded");
            int min_X = config.getInt("location.min.X");
            int min_Y = config.getInt("location.min.Y");
            int min_Z = config.getInt("location.min.Z");
            Location min = new Location(min_b_world,min_X,min_Y,min_Z);

            String max_s_world = config.getString("location.max.world");
            World max_b_world = getServer().getWorld(max_s_world);
            Objects.requireNonNull(max_b_world,"Max location world not loaded");
            int max_X = config.getInt("location.max.X");
            int max_Y = config.getInt("location.max.Y");
            int max_Z = config.getInt("location.max.Z");
            Location max = new Location(max_b_world,max_X,max_Y,max_Z);

            String center_s_world = config.getString("location.center.world");
            World center_b_world = getServer().getWorld(center_s_world);
            Objects.requireNonNull(center_b_world,"Center location world not loaded");
            int center_X = config.getInt("location.center.X");
            int center_Y = config.getInt("location.center.Y");
            int center_Z = config.getInt("location.center.Z");
            Location center = new Location(center_b_world,center_X,center_Y,center_Z);

            LightZone zone = new LightZone(id,min,max);
            zone.setStart(config.getInt("time.start"));
            zone.setEnd(config.getInt("time.end"));
            zone.setCenter(center);
            zone.setLevel(config.getInt("level",15));
            int start = (int) (Math.min(zone.getStart(), zone.getEnd()));
            int end = (int) (Math.max(zone.getStart(), zone.getEnd()));
            boolean turnOn = IntStream.range(start, end).anyMatch(r->r==zone.getLocation().getWorld().getTime());
            zone.setState(!turnOn);

            lightZones.add(zone);

            controller = zone;
        }
        return controller;
    }

    public void saveController(@NotNull LightController controller){
        String type = controller instanceof LightBlock ? "block" : "zone";
        File file = new File(getDataFolder(),"/lights/"+controller.getId().toString().split(Pattern.quote("-"))[0]+".yml");
        int start = (int) (Math.min(controller.getStart(), controller.getEnd()));
        int end = (int) (Math.max(controller.getStart(), controller.getEnd()));
        boolean turnOn = IntStream.range(start, end).anyMatch(r->r==controller.getLocation().getWorld().getTime());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.options().header("Please do not edit this file manually!"+"\n"+"Changing this file can cause errors to occur!");
        config.set("id",controller.getId().toString());
        config.set("type",type);
        config.set("level",controller.getLevel());
        config.set("time.start",controller.getStart());
        config.set("time.end",controller.getEnd());
        if(type.equals("block")) {
            LightBlock block = (LightBlock) controller;
            block.setState(!turnOn);
            Location loc = block.getLocation();
            config.set("location.world",loc.getWorld().getName());
            config.set("location.X",loc.getBlockX());
            config.set("location.Y",loc.getBlockY());
            config.set("location.Z",loc.getBlockZ());
            boolean update = !lightBlocks.add(block);
            if(update){
                LightBlock old = lightBlocks.stream().filter(l->l.getId().equals(block.getId())).findFirst().orElse(null);
                if(old != null){
                    old.setEnd(block.getEnd());
                    old.setLevel(block.getLevel());
                    old.setStart(block.getStart());
                    old.setState(!turnOn);
                }
            }
        } else {
            LightZone zone = (LightZone) controller;
            zone.setState(!turnOn);
            Location min = zone.getMin();
            config.set("location.min.world",min.getWorld().getName());
            config.set("location.min.X",min.getBlockX());
            config.set("location.min.Y",min.getBlockY());
            config.set("location.min.Z",min.getBlockZ());
            Location max = zone.getMax();
            config.set("location.max.world",max.getWorld().getName());
            config.set("location.max.X",max.getBlockX());
            config.set("location.max.Y",max.getBlockY());
            config.set("location.max.Z",max.getBlockZ());
            Location center = zone.getLocation();
            config.set("location.center.world",center.getWorld().getName());
            config.set("location.center.X",center.getBlockX());
            config.set("location.center.Y",center.getBlockY());
            config.set("location.center.Z",center.getBlockZ());
            boolean update = !lightZones.add(zone);
            if(update){
                LightZone old = lightZones.stream().filter(l->l.getId().equals(zone.getId())).findFirst().orElse(null);
                if(old != null){
                    old.setEnd(zone.getEnd());
                    old.setLevel(zone.getLevel());
                    old.setStart(zone.getStart());
                    old.setState(!turnOn);
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            if(isDebug())
                getLogger().warning("Failed to save light controller file '"+file.getPath()+"': "+e);
        }
    }

    public boolean removeController(@NotNull UUID uid){
        LightBlock block = lightBlocks.stream().filter(light -> light.getId().equals(uid)).findFirst().orElse(null);
        LightZone zone = lightZones.stream().filter(light -> light.getId().equals(uid)).findFirst().orElse(null);
        File file = new File(getDataFolder(),"/lights/"+uid.toString().split(Pattern.quote("-"))[0]+".yml");
        if(block != null){
            lightBlocks.remove(block);
        }
        if(zone != null){
            lightZones.remove(zone);
        }
        return file.delete();
    }

    @NotNull
    public LightController getController(@NotNull UUID uid)
            throws NullPointerException
    {
        LightController controller = lightBlocks.stream().filter(light -> light.getId().equals(uid)).findFirst().orElse(null);
        if(controller == null)
            controller = lightZones.stream().filter(light -> light.getId().equals(uid)).findFirst().orElse(null);
        return Objects.requireNonNull(controller,"Light controller not found");
    }

    @NotNull
    public static String formatLocation(@Nullable Location loc){
        if(loc == null)
            return "?;?;?;?";
        return (loc.getWorld() != null ? loc.getWorld().getName() + ";" : "")+loc.getBlockX()+";"+loc.getBlockY()+";"+loc.getBlockZ();
    }

    @Override
    public @NotNull Set<LightBlock> getLightBlocks() {
        return new HashSet<>(lightBlocks);
    }

    @Override
    public @NotNull Set<LightZone> getLightZones() {
        return new HashSet<>(lightZones);
    }
}
