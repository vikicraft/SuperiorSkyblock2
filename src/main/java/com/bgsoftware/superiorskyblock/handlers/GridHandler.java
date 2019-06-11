package com.bgsoftware.superiorskyblock.handlers;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.PreIslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.database.Query;
import com.bgsoftware.superiorskyblock.gui.GUIInventory;
import com.bgsoftware.superiorskyblock.island.SIsland;
import com.bgsoftware.superiorskyblock.menu.IslandBiomesMenu;
import com.bgsoftware.superiorskyblock.menu.IslandCreationMenu;
import com.bgsoftware.superiorskyblock.menu.IslandValuesMenu;
import com.bgsoftware.superiorskyblock.menu.IslandWarpsMenu;
import com.bgsoftware.superiorskyblock.menu.IslandsTopMenu;
import com.bgsoftware.superiorskyblock.utils.jnbt.CompoundTag;
import com.bgsoftware.superiorskyblock.utils.jnbt.IntTag;
import com.bgsoftware.superiorskyblock.utils.jnbt.ListTag;
import com.bgsoftware.superiorskyblock.utils.jnbt.StringTag;
import com.bgsoftware.superiorskyblock.utils.jnbt.Tag;
import com.bgsoftware.superiorskyblock.utils.threads.SuperiorThread;
import com.bgsoftware.superiorskyblock.wrappers.SSuperiorPlayer;
import com.bgsoftware.superiorskyblock.wrappers.SBlockPosition;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.island.IslandRegistry;
import com.bgsoftware.superiorskyblock.island.SpawnIsland;
import com.bgsoftware.superiorskyblock.utils.FileUtil;
import com.bgsoftware.superiorskyblock.utils.ItemBuilder;
import com.bgsoftware.superiorskyblock.utils.key.SKey;
import com.bgsoftware.superiorskyblock.utils.key.KeyMap;
import com.bgsoftware.superiorskyblock.utils.queue.Queue;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class GridHandler implements GridManager {

    private SuperiorSkyblockPlugin plugin;

    private Queue<CreateIslandData> islandCreationsQueue = new Queue<>();
    private boolean creationProgress = false;

    private IslandRegistry islands = new IslandRegistry();
    private StackedBlocksHandler stackedBlocks = new StackedBlocksHandler();
    private BlockValuesHandler blockValues = new BlockValuesHandler();

    private SIsland spawnIsland;
    private SBlockPosition lastIsland;

    public GridHandler(SuperiorSkyblockPlugin plugin){
        this.plugin = plugin;
        lastIsland = SBlockPosition.of(plugin.getSettings().islandWorld, 0, 100, 0);
        spawnIsland = new SpawnIsland();

        //Init all panels
        Bukkit.getScheduler().runTask(SuperiorSkyblockPlugin.getPlugin(), () -> {
            IslandsTopMenu.init();
            IslandValuesMenu.init();
            IslandWarpsMenu.init();
            IslandBiomesMenu.init();
            IslandCreationMenu.init();
        });
    }

    public void createIsland(ResultSet resultSet) throws SQLException {
        UUID owner = UUID.fromString(resultSet.getString("owner"));
        islands.add(owner, new SIsland(resultSet));
    }

    public void createIsland(CompoundTag tag){
        UUID owner = UUID.fromString(((StringTag) tag.getValue().get("owner")).getValue());
        Island island = new SIsland(tag);
        islands.add(owner, island);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getDataHandler().insertIsland(island));
    }

    @Override
    public void createIsland(SuperiorPlayer superiorPlayer, String schemName, BigDecimal bonus, Biome biome){
        if(creationProgress) {
            islandCreationsQueue.push(new CreateIslandData(superiorPlayer.getUniqueId(), schemName, bonus, biome));
            return;
        }

        PreIslandCreateEvent preIslandCreateEvent = new PreIslandCreateEvent(superiorPlayer);
        Bukkit.getPluginManager().callEvent(preIslandCreateEvent);

        if(!preIslandCreateEvent.isCancelled()) {
            long startTime = System.currentTimeMillis();
            creationProgress = true;

            Location islandLocation = getNextLocation();
            Island island = new SIsland(superiorPlayer, islandLocation.add(0.5, 0, 0.5));

            IslandCreateEvent islandCreateEvent = new IslandCreateEvent(superiorPlayer, island);
            Bukkit.getPluginManager().callEvent(islandCreateEvent);

            if(!islandCreateEvent.isCancelled()) {
                islands.add(superiorPlayer.getUniqueId(), island);
                setLastIsland(SBlockPosition.of(islandLocation));

                for (Chunk chunk : island.getAllChunks(true)) {
                    chunk.getWorld().regenerateChunk(chunk.getX(), chunk.getZ());
                    plugin.getNMSAdapter().refreshChunk(chunk);
                }

                Schematic schematic = plugin.getSchematics().getSchematic(schemName);
                schematic.pasteSchematic(islandLocation.getBlock().getRelative(BlockFace.DOWN).getLocation(), () -> {
                    island.getAllChunks(true).forEach(chunk -> plugin.getNMSAdapter().refreshChunk(chunk));
                    island.setBonusWorth(bonus);
                    island.setBiome(biome);
                    if (superiorPlayer.asOfflinePlayer().isOnline()) {
                        Locale.CREATE_ISLAND.send(superiorPlayer, SBlockPosition.of(islandLocation), System.currentTimeMillis() - startTime);
                        if (islandCreateEvent.canTeleport()) {
                            superiorPlayer.asPlayer().teleport(islandLocation);
                            if (island.isInside(superiorPlayer.getLocation()))
                                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getNMSAdapter().setWorldBorder(superiorPlayer, island), 20L);
                        }
                        new SuperiorThread(() -> island.calcIslandWorth(null)).start();
                    }
                });

                plugin.getDataHandler().insertIsland(island);
            }

            creationProgress = false;
        }

        if(islandCreationsQueue.size() != 0){
            CreateIslandData data = islandCreationsQueue.pop();
            createIsland(SSuperiorPlayer.of(data.player), data.schemName, data.bonus, data.biome);
        }
    }

    @Override
    public void createIsland(SuperiorPlayer superiorPlayer, String schemName, BigDecimal bonus) {
        createIsland(superiorPlayer, schemName, bonus, Biome.PLAINS);
    }

    @Override
    public void createIsland(SuperiorPlayer superiorPlayer, String schemName){
        createIsland(superiorPlayer, schemName, BigDecimal.ZERO);
    }

    @Override
    public void deleteIsland(Island island){
        SuperiorPlayer targetPlayer;
        for(UUID uuid : island.allPlayersInside()){
            targetPlayer = SSuperiorPlayer.of(uuid);
            targetPlayer.asPlayer().teleport(plugin.getSettings().getSpawnAsBukkitLocation());
            Locale.ISLAND_GOT_DELETED_WHILE_INSIDE.send(targetPlayer);
        }
        islands.remove(island.getOwner().getUniqueId());
        plugin.getDataHandler().deleteIsland(island);
    }

    @Override
    public Island getIsland(SuperiorPlayer superiorPlayer){
        return getIsland(superiorPlayer.getTeamLeader());
    }

    @Override
    public Island getIsland(UUID uuid){
        return islands.get(uuid);
    }

    @Override
    public Island getIsland(int index){
        return index >= islands.size() ? null : islands.get(index);
    }

    @Override
    public Island getIslandAt(Location location){
        if(location == null || !location.getWorld().getName().equals(plugin.getSettings().islandWorld))
            return null;

        if(spawnIsland.isInside(location))
            return spawnIsland;

        Iterator<Island> islands = this.islands.iterator();
        Island island;

        while(islands.hasNext()){
            island = islands.next();
            if(island.isInside(location))
                return island;
        }

        return null;
    }

    @Override
    public Island getSpawnIsland(){
        return spawnIsland;
    }

    @Override
    public World getIslandsWorld() {
        return Bukkit.getWorld(plugin.getSettings().islandWorld);
    }

    @Override
    public Location getNextLocation(){
        Location location = lastIsland.parse().clone();
        BlockFace islandFace = getIslandFace();

        int islandRange = plugin.getSettings().maxIslandSize * 3;

        if(islandFace == BlockFace.NORTH){
            location.add(islandRange, 0, 0);
        }else if(islandFace == BlockFace.EAST){
            if(lastIsland.getX() == -lastIsland.getZ())
                location.add(islandRange, 0, 0);
            else if(lastIsland.getX() == lastIsland.getZ())
                location.subtract(islandRange, 0, 0);
            else
                location.add(0, 0, islandRange);
        }else if(islandFace == BlockFace.SOUTH){
            if(lastIsland.getX() == -lastIsland.getZ())
                location.subtract(0, 0, islandRange);
            else
                location.subtract(islandRange, 0, 0);
        }else if(islandFace == BlockFace.WEST){
            if(lastIsland.getX() == lastIsland.getZ())
                location.add(islandRange, 0, 0);
            else
                location.subtract(0, 0, islandRange);
        }

        if(getIslandAt(location) != null){
            lastIsland = SBlockPosition.of(location);
            return getNextLocation();
        }

        return location;
    }

    @Override
    public List<UUID> getAllIslands(){
        return Lists.newArrayList(islands.uuidIterator());
    }

    @Override
    public int getBlockValue(Key key){
        return blockValues.getBlockValue(key);
    }

    public Key getBlockValueKey(Key key){
        return blockValues.blockValues.getKey(key);
    }

    @Override
    public int getBlockAmount(Block block){
        return getBlockAmount(block.getLocation());
    }

    @Override
    public int getBlockAmount(Location location){
        return stackedBlocks.getOrDefault(SBlockPosition.of(location), 1);
    }

    @Override
    public void setBlockAmount(Block block, int amount){
        boolean insert = false;

        if(!stackedBlocks.stackedBlocks.containsKey(SBlockPosition.of(block.getLocation())))
            insert = true;

        stackedBlocks.put(SBlockPosition.of(block.getLocation()), amount);
        stackedBlocks.updateName(block);

        if(amount > 1) {
            if (insert) {
                Query.STACKED_BLOCKS_INSERT.getStatementHolder()
                        .setString(block.getWorld().getName())
                        .setInt(block.getX())
                        .setInt(block.getY())
                        .setInt(block.getZ())
                        .setInt(amount)
                        .execute(true);
            } else {
                Query.STACKED_BLOCKS_UPDATE.getStatementHolder()
                        .setInt(amount)
                        .setString(block.getWorld().getName())
                        .setInt(block.getX())
                        .setInt(block.getY())
                        .setInt(block.getZ())
                        .execute(true);
            }
        }else{
            Query.STACKED_BLOCKS_DELETE.getStatementHolder()
                    .setString(block.getWorld().getName())
                    .setInt(block.getX())
                    .setInt(block.getY())
                    .setInt(block.getZ())
                    .execute(true);
        }
    }

    @Override
    public void openTopIslands(SuperiorPlayer superiorPlayer){
        IslandsTopMenu.createInventory().openInventory(superiorPlayer, null);
    }

    @Override
    public void calcAllIslands(){
        for (Island island : islands)
            island.calcIslandWorth(null);
    }

    public void loadGrid(ResultSet resultSet) throws SQLException{
        lastIsland = SBlockPosition.of(resultSet.getString("lastIsland"));

        for(String entry : resultSet.getString("stackedBlocks").split(";")){
            if(!entry.isEmpty()) {
                String[] sections = entry.split("=");
                stackedBlocks.put(SBlockPosition.of(sections[0]), Integer.valueOf(sections[1]));
            }
        }

        int maxIslandSize = resultSet.getInt("maxIslandSize");
        String world = resultSet.getString("world");

        if(plugin.getSettings().maxIslandSize != maxIslandSize){
            SuperiorSkyblockPlugin.log("You have changed the max-island-size value without deleting database.");
            SuperiorSkyblockPlugin.log("Restoring it to the old value...");
            plugin.getSettings().updateValue("max-island-size", maxIslandSize);
        }

        if(!plugin.getSettings().islandWorld.equals(world)){
            SuperiorSkyblockPlugin.log("You have changed the island-world value without deleting database.");
            SuperiorSkyblockPlugin.log("Restoring it to the old value...");
            plugin.getSettings().updateValue("island-world", world);
        }
    }

    public void loadStackedBlocks(ResultSet set) throws SQLException {
        String world = set.getString("world");
        int x = set.getInt("x");
        int y = set.getInt("y");
        int z = set.getInt("z");
        int amount = set.getInt("amount");

        stackedBlocks.put(SBlockPosition.of(world, x, y, z), amount);
    }

    public void executeStackedBlocksInsertStatement(boolean async){
        for (SBlockPosition position : stackedBlocks.stackedBlocks.keySet()) {
            Query.STACKED_BLOCKS_INSERT.getStatementHolder()
                    .setString(position.getWorld().getName())
                    .setInt(position.getX())
                    .setInt(position.getY())
                    .setInt(position.getZ())
                    .setInt(stackedBlocks.stackedBlocks.get(position))
                    .execute(async);
        }
    }

    public void loadGrid(CompoundTag tag){
        Map<String, Tag> compoundValues = tag.getValue(), _compoundValues;

        lastIsland = SBlockPosition.of(((StringTag) compoundValues.get("lastIsland")).getValue());

        for(Tag _tag : ((ListTag) compoundValues.get("stackedBlocks")).getValue()){
            _compoundValues = ((CompoundTag) _tag).getValue();
            String location = ((StringTag) _compoundValues.get("location")).getValue();
            int stackAmount = ((IntTag) _compoundValues.get("stackAmount")).getValue();
            stackedBlocks.put(SBlockPosition.of(location), stackAmount);
        }

        int maxIslandSize = ((IntTag) compoundValues.getOrDefault("maxIslandSize", new IntTag(plugin.getSettings().maxIslandSize))).getValue();
        if(plugin.getSettings().maxIslandSize != maxIslandSize){
            SuperiorSkyblockPlugin.log("You have changed the max-island-size value without deleting database.");
            SuperiorSkyblockPlugin.log("Restoring it to the old value...");
            plugin.getSettings().updateValue("max-island-size", maxIslandSize);
        }

    }

    public void executeGridInsertStatement(boolean async) {
        Query.GRID_INSERT.getStatementHolder()
                .setString(this.lastIsland.toString())
                .setString("")
                .setInt(plugin.getSettings().maxIslandSize)
                .setString(plugin.getSettings().islandWorld)
                .execute(async);
    }

    public void reloadBlockValues(){
        blockValues = new BlockValuesHandler();
    }

    private BlockFace getIslandFace(){
        //Possibilities: North / East
        if(lastIsland.getX() >= lastIsland.getZ()) {
            return -lastIsland.getX() > lastIsland.getZ() ? BlockFace.NORTH : BlockFace.EAST;
        }
        //Possibilities: South / West
        else{
            return -lastIsland.getX() > lastIsland.getZ() ? BlockFace.WEST : BlockFace.SOUTH;
        }
    }

    private void setLastIsland(SBlockPosition blockPosition){
        this.lastIsland = blockPosition;
        Query.GRID_UPDATE.getStatementHolder()
                .setString(blockPosition.toString())
                .execute(true);
    }

    private class CreateIslandData {

        public UUID player;
        public String schemName;
        public BigDecimal bonus;
        public Biome biome;

        public CreateIslandData(UUID player, String schemName, BigDecimal bonus, Biome biome){
            this.player = player;
            this.schemName = schemName;
            this.bonus = bonus;
            this.biome = biome;
        }

    }

    private class StackedBlocksHandler {

        private Map<SBlockPosition, Integer> stackedBlocks = Maps.newHashMap();

        void put(SBlockPosition location, int amount){
            stackedBlocks.put(location, amount);
        }

        @SuppressWarnings("SameParameterValue")
        int getOrDefault(SBlockPosition location, int def){
            return stackedBlocks.getOrDefault(location, def);
        }

        Set<Map.Entry<SBlockPosition, Integer>> entrySet(){
            return stackedBlocks.entrySet();
        }

        private void updateName(Block block){
            int amount = getBlockAmount(block);
            ArmorStand armorStand = getHologram(block);

            if(amount <= 1){
                stackedBlocks.remove(SBlockPosition.of(block.getLocation()));
                armorStand.remove();
            }else{
                armorStand.setCustomName(plugin.getSettings().stackedBlocksName
                        .replace("{0}", String.valueOf(amount))
                        .replace("{1}", getFormattedType(block.getType().name()))
                );
            }

        }

        private ArmorStand getHologram(Block block){
            Location hologramLocation = block.getLocation().add(0.5, 1, 0.5);

            // Looking for an armorstand
            for(Entity entity : block.getChunk().getEntities()){
                if(entity instanceof ArmorStand && entity.getLocation().equals(hologramLocation)){
                    return (ArmorStand) entity;
                }
            }

            // Couldn't find one, creating one...

            ArmorStand armorStand = block.getWorld().spawn(hologramLocation, ArmorStand.class);

            armorStand.setGravity(false);
            armorStand.setSmall(true);
            armorStand.setVisible(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setMarker(true);

            return armorStand;
        }

        private String getFormattedType(String type){
            StringBuilder stringBuilder = new StringBuilder();

            for(String section : type.split("_")){
                stringBuilder.append(" ").append(section.substring(0, 1).toUpperCase()).append(section.substring(1).toLowerCase());
            }

            return stringBuilder.toString().substring(1);
        }

    }

    private class BlockValuesHandler {

        private final KeyMap<Integer> blockValues = new KeyMap<>();

        private BlockValuesHandler(){
            SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

            File file = new File(plugin.getDataFolder(), "blockvalues.yml");

            if(!file.exists())
                plugin.saveResource("blockvalues.yml", true);

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

            for(String key : cfg.getConfigurationSection("block-values").getKeys(false))
                blockValues.put(SKey.of(key), cfg.getInt("block-values." + key));
        }

        int getBlockValue(Key key) {
            return blockValues.getOrDefault(key, 0);
        }
    }

    public IslandRegistry getIslands() {
        return islands;
    }
}
