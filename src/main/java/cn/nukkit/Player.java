package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySign;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.data.*;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityEgg;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntitySnowball;
import cn.nukkit.event.TextContainer;
import cn.nukkit.event.TranslationContainer;
import cn.nukkit.event.block.SignChangeEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArrow;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemGlassBottle;
import cn.nukkit.item.food.Food;
import cn.nukkit.level.ChunkLoader;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.particle.CriticalParticle;
import cn.nukkit.level.sound.ClickSound;
import cn.nukkit.level.sound.LaunchSound;
import cn.nukkit.math.*;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.utils.Zlib;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

//import cn.nukkit.entity.Item;


/**
 * author: MagicDroidX & Box
 * Nukkit Project
 */
public class Player extends EntityHuman implements CommandSender, InventoryHolder, ChunkLoader, IPlayer {

    public static final int SURVIVAL = 0;
    public static final int CREATIVE = 1;
    public static final int ADVENTURE = 2;
    public static final int SPECTATOR = 3;
    public static final int VIEW = SPECTATOR;

    public static final int SURVIVAL_SLOTS = 36;
    public static final int CREATIVE_SLOTS = 112;

    protected SourceInterface interfaz;

    public boolean playedBefore;
    public boolean spawned = false;
    public boolean loggedIn = false;
    public int gamemode;
    public long lastBreak;

    protected int windowCnt = 2;

    protected Map<Inventory, Integer> windows;

    protected Map<Integer, Inventory> windowIndex = new HashMap<>();

    protected int messageCounter = 2;

    protected int sendIndex = 0;

    private String clientSecret;

    public Vector3 speed = null;

    public boolean blocked = false;

    //todo: achievements

    protected SimpleTransactionGroup currentTransaction = null;

    public int craftingType = 0; //0 = 2x2 crafting, 1 = 3x3 crafting, 2 = stonecutter

    protected boolean isCrafting = false;

    public long creationTime = 0;

    protected long randomClientId;

    protected int protocol;

    protected double lastMovement = 0;

    protected Vector3 forceMovement = null;

    protected Vector3 teleportPosition = null;

    protected boolean connected = true;
    protected String ip;
    protected boolean removeFormat = true;

    protected int port;
    protected String username;
    protected String iusername;
    protected String displayName;

    protected int startAction = -1;

    protected Vector3 sleeping = null;
    protected Long clientID = null;

    private Integer loaderId = null;

    protected float stepHeight = 0.6f;

    public Map<String, Boolean> usedChunks = new HashMap<>();

    protected int chunkLoadCount = 0;
    protected Map<String, Integer> loadQueue = new HashMap<>();
    protected int nextChunkOrderRun = 5;

    protected Map<UUID, Player> hiddenPlayers = new HashMap<>();

    protected Vector3 newPosition = null;

    public int viewDistance;
    protected int chunksPerTick;
    protected int spawnThreshold;

    private Position spawnPosition = null;

    protected int inAirTicks = 0;
    protected int startAirTicks = 5;

    protected boolean autoJump = true;

    protected boolean allowFlight = false;

    private Map<Integer, Boolean> needACK = new HashMap<>();

    private Map<Integer, List<DataPacket>> batchedPackets = new TreeMap<>();

    private PermissibleBase perm = null;

    private int exp = 0;
    private int expLevel = 0;

    private PlayerFood foodData = null;

    private Entity killer = null;

    private final AtomicReference<Locale> locale = new AtomicReference<>(null);

    private int hash;
    protected boolean shouldSendStatus;

    public TranslationContainer getLeaveMessage() {
        return new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.left", this.getDisplayName());
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public Long getClientId() {
        return clientID;
    }

    @Override
    public boolean isBanned() {
        return ServerInfo.banByName.contains(this.getName());
    }

    @Override
    public void setBanned(boolean value) {
        if (value) {
            ServerInfo.banByName.add(this.getName());
            this.kick("You have been banned");
        } else
            ServerInfo.banByName.remove(this.getName());
    }

    @Override
    public boolean isWhitelisted() {
        return ServerInfo.whitelist.contains(getName());
    }

    @Override
    public void setWhitelisted(boolean value) {
        if (value) ServerInfo.whitelist.add(getName());
        else ServerInfo.whitelist.remove(getName());
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public Long getFirstPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("firstPlayed") : null;
    }

    @Override
    public Long getLastPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("lastPlayed") : null;
    }

    @Override
    public boolean hasPlayedBefore() {
        return this.playedBefore;
    }

    public void setAllowFlight(boolean value) {
        this.allowFlight = value;
        this.inAirTicks = 0;
        this.sendSettings();
    }

    public boolean getAllowFlight() {
        return allowFlight;
    }

    public void setAutoJump(boolean value) {
        this.autoJump = value;
        this.sendSettings();
    }

    public boolean hasAutoJump() {
        return autoJump;
    }

    @Override
    public void spawnTo(Player player) {
        if (this.spawned && player.spawned && this.isAlive() && player.isAlive() && player.getLevel() == this.level && player.canSee(this) && !this.isSpectator()) {
            super.spawnTo(player);
        }
    }

    @Override
    public Server getServer() {
        return this.server;
    }

    public boolean getRemoveFormat() {
        return removeFormat;
    }

    public void setRemoveFormat() {
        this.setRemoveFormat(true);
    }

    public void setRemoveFormat(boolean remove) {
        this.removeFormat = remove;
    }

    public boolean canSee(Player player) {
        return !this.hiddenPlayers.containsKey(player.getUniqueId());
    }

    public void hidePlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.put(player.getUniqueId(), player);
        player.despawnFrom(this);
    }

    public void showPlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.spawnTo(this);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public void resetFallDistance() {
        super.resetFallDistance();
        if (this.inAirTicks != 0) {
            this.startAirTicks = 5;
        }
        this.inAirTicks = 0;
        this.highestPosition = this.y;
    }

    @Override
    public boolean isOnline() {
        return this.connected && this.loggedIn;
    }

    @Override
    public boolean isOp() {
        return ServerInfo.operators.contains(getName());
    }

    @Override
    public void setOp(boolean value) {
        if (value == this.isOp())
            return;
        if (value) ServerInfo.operators.add(getName());
        else ServerInfo.operators.remove(getName());
        this.recalculatePermissions();
    }

    @Override
    public boolean isPermissionSet(String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return this.perm.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String name) {
        return this.perm != null && this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.addAttachment(plugin, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.addAttachment(plugin, name, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);

        if (this.perm == null) {
            return;
        }

        this.perm.recalculatePermissions();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    public Player(SourceInterface interfaz, Long clientID, String ip, int port) {
        super(null, new CompoundTag());
        this.interfaz = interfaz;
        this.windows = new HashMap<>();
        this.perm = new PermissibleBase(this);
        this.server = Server.getInstance();
        this.lastBreak = Long.MAX_VALUE;
        this.ip = ip;
        this.port = port;
        this.clientID = clientID;
        this.loaderId = Level.generateChunkLoaderId(this);
        this.chunksPerTick = ServerProperties.chunks_sending_per_tick;
        this.spawnThreshold = ServerProperties.spawn_threshold;
        this.spawnPosition = null;
        this.gamemode = ServerProperties.gamemode;
        this.setLevel(this.server.getDefaultLevel());
        this.viewDistance = ServerProperties.view_distance;
        //this.newPosition = new Vector3(0, 0, 0);
        this.boundingBox = new AxisAlignedBB(0, 0, 0, 0, 0, 0);

        this.uuid = null;
        this.rawUUID = null;

        this.creationTime = System.currentTimeMillis();
    }

    public boolean isPlayer() {
        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.getDisplayName(), this.getSkin());
        }
    }

    @Override
    public void setSkin(Skin skin) {
        super.setSkin(skin);
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.getDisplayName(), skin);
        }
    }

    public String getAddress() {
        return this.ip;
    }

    public int getPort() {
        return port;
    }

    public Position getNextPosition() {
        return this.newPosition != null ? new Position(this.newPosition.x, this.newPosition.y, this.newPosition.z, this.level) : this.getPosition();
    }

    public boolean isSleeping() {
        return this.sleeping != null;
    }

    public int getInAirTicks() {
        return this.inAirTicks;
    }

    @Override
    public boolean switchLevel(Level targetLevel) {
        Level oldLevel = this.level;
        if (super.switchLevel(targetLevel)) {
            for (String index : new ArrayList<>(this.usedChunks.keySet())) {
                Chunk.Entry chunkEntry = Level.getChunkXZ(index);
                int chunkX = chunkEntry.chunkX;
                int chunkZ = chunkEntry.chunkZ;
                this.unloadChunk(chunkX, chunkZ, oldLevel);
            }

            this.usedChunks = new HashMap<>();
            SetTimePacket pk = new SetTimePacket();
            pk.time = this.level.getTime();
            pk.started = !this.level.stopTime;
            this.dataPacket(pk);
            targetLevel.sendWeather(this);
            if (targetLevel.getDimension() != oldLevel.getDimension()) {
                ChangeDimensionPacket p = new ChangeDimensionPacket();
                p.dimension = (byte) targetLevel.getDimension();
                this.dataPacket(p);
                this.shouldSendStatus = true;
            }

            if (this.spawned) {
                this.spawnToAll();
            }
            return true;
        }
        return false;
    }

    public void unloadChunk(int x, int z) {
        this.unloadChunk(x, z, null);
    }

    public void unloadChunk(int x, int z, Level level) {
        level = level == null ? this.level : level;
        String index = Level.chunkHash(x, z);
        if (this.usedChunks.containsKey(index)) {
            for (Entity entity : level.getChunkEntities(x, z).values()) {
                if (entity != this) {
                    entity.despawnFrom(this);
                }
            }

            this.usedChunks.remove(index);
        }
        level.unregisterChunkLoader(this, x, z);
        this.loadQueue.remove(index);
    }

    public Position getSpawn() {
        if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
            return this.spawnPosition;
        } else {
            return this.server.getDefaultLevel().getSafeSpawn();
        }
    }

    public void sendChunk(int x, int z, DataPacket packet) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), true);
        this.chunkLoadCount++;

        this.dataPacket(packet);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    public void sendChunk(int x, int z, byte[] payload) {
        this.sendChunk(x, z, payload, FullChunkDataPacket.ORDER_COLUMNS);
    }

    public void sendChunk(int x, int z, byte[] payload, byte ordering) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), true);
        this.chunkLoadCount++;

        FullChunkDataPacket pk = new FullChunkDataPacket();
        pk.chunkX = x;
        pk.chunkZ = z;
        pk.order = ordering;
        pk.data = payload;

        this.batchDataPacket(pk);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    protected void sendNextChunk() {
        if (!this.connected) {
            return;
        }

        int count = 0;

        List<Map.Entry<String, Integer>> entryList = new ArrayList<>(this.loadQueue.entrySet());
        entryList.sort(Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<String, Integer> entry : entryList) {
            String index = entry.getKey();
            if (count >= this.chunksPerTick) {
                break;
            }

            Chunk.Entry chunkEntry = Level.getChunkXZ(index);
            int chunkX = chunkEntry.chunkX;
            int chunkZ = chunkEntry.chunkZ;

            ++count;

            this.usedChunks.put(index, false);
            this.level.registerChunkLoader(this, chunkX, chunkZ, false);

            if (!this.level.populateChunk(chunkX, chunkZ)) {
                if (this.spawned && this.teleportPosition == null) {
                    continue;
                } else {
                    break;
                }
            }

            this.loadQueue.remove(index);

            PlayerChunkRequestEvent ev = new PlayerChunkRequestEvent(this, chunkX, chunkZ);
            this.server.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) {
                this.level.requestChunk(chunkX, chunkZ, this);
            }
            if (this.loadQueue.isEmpty() && this.shouldSendStatus) {
                this.shouldSendStatus = false;

                PlayStatusPacket pk = new PlayStatusPacket();
                pk.status = PlayStatusPacket.PLAYER_SPAWN;
                this.dataPacket(pk);
            }
        }

        if (this.chunkLoadCount >= this.spawnThreshold && !this.spawned && this.teleportPosition == null) {
            this.doFirstSpawn();
        }
    }

    public void doFirstSpawn() {

        this.spawned = true;

        this.server.sendRecipeList(this);
        this.sendSettings();

        this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.getDisplayName(), this.getSkin());
        this.server.sendFullPlayerListData(this, false);

        this.sendPotionEffects(this);
        this.sendData(this);
        this.inventory.sendContents(this);
        this.inventory.sendArmorContents(this);

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        setTimePacket.started = !this.level.stopTime;
        this.dataPacket(setTimePacket);

        Position pos = this.level.getSafeSpawn(this);

        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(this, pos);

        this.server.getPluginManager().callEvent(respawnEvent);

        pos = respawnEvent.getRespawnPosition();

        RespawnPacket respawnPacket = new RespawnPacket();
        respawnPacket.x = (float) pos.x;
        respawnPacket.y = (float) pos.y;
        respawnPacket.z = (float) pos.z;
        this.dataPacket(respawnPacket);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.status = PlayStatusPacket.PLAYER_SPAWN;
        this.dataPacket(playStatusPacket);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this,
                new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.joined", new String[]{
                        this.getDisplayName()
                })
        );

        this.server.getPluginManager().callEvent(playerJoinEvent);

        if (!playerJoinEvent.getJoinMessage().toString().trim().isEmpty()) {
            this.server.broadcastMessage(playerJoinEvent.getJoinMessage());
        }

        this.noDamageTicks = 60;

        for (String index : this.usedChunks.keySet()) {
            Chunk.Entry chunkEntry = Level.getChunkXZ(index);
            int chunkX = chunkEntry.chunkX;
            int chunkZ = chunkEntry.chunkZ;
            for (Entity entity : this.level.getChunkEntities(chunkX, chunkZ).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        this.sendExperience(this.getExperience());
        this.sendExperienceLevel(this.getExperienceLevel());

        this.teleport(pos, null); // Prevent PlayerTeleportEvent during player spawn

        if (!this.isSpectator()) {
            this.spawnToAll();
        }

        //todo Updater

        if (this.getHealth() <= 0) {
            respawnPacket = new RespawnPacket();
            pos = this.getSpawn();
            respawnPacket.x = (float) pos.x;
            respawnPacket.y = (float) pos.y;
            respawnPacket.z = (float) pos.z;
            this.dataPacket(respawnPacket);
        }

        //Weather
        this.getLevel().sendWeather(this);

        //FoodLevel
        this.getFoodData().sendFoodLevel();
    }

    protected boolean orderChunks() {
        if (!this.connected) {
            return false;
        }

        this.nextChunkOrderRun = 200;

        Map<String, Integer> newOrder = new HashMap<>();
        Map<String, Boolean> lastChunk = new HashMap<>(this.usedChunks);

        int centerX = (int) this.x >> 4;
        int centerZ = (int) this.z >> 4;
        int count = 0;

        for (int x = -this.viewDistance; x <= this.viewDistance; x++) {
            for (int z = -this.viewDistance; z <= this.viewDistance; z++) {
                int chunkX = x + centerX;
                int chunkZ = z + centerZ;
                int distance = (int) Math.sqrt((double) x * x + (double) z * z);
                if (distance <= this.viewDistance) {
                    String index;
                    if (!(this.usedChunks.containsKey(index = Level.chunkHash(chunkX, chunkZ))) || !this.usedChunks.get(index)) {
                        newOrder.put(index, distance);
                        count++;
                    }
                    lastChunk.remove(index);
                }
            }
        }

        for (String index : new ArrayList<>(lastChunk.keySet())) {
            Chunk.Entry entry = Level.getChunkXZ(index);
            this.unloadChunk(entry.chunkX, entry.chunkZ);
        }

        this.loadQueue = newOrder;

        return true;
    }

    public boolean batchDataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        DataPacketSendEvent event = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        if (!this.batchedPackets.containsKey(packet.getChannel())) {
            this.batchedPackets.put(packet.getChannel(), new ArrayList<>());
        }

        this.batchedPackets.get(packet.getChannel()).add(packet.clone());

        return true;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     */
    public boolean dataPacket(DataPacket packet) {
        return this.dataPacket(packet, false) != -1;
    }

    public int dataPacket(DataPacket packet, boolean needACK) {
        if (!this.connected) {
            return -1;
        }

        DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return -1;
        }

        Integer identifier = this.interfaz.putPacket(this, packet, needACK, false);

        if (needACK && identifier != null) {
            this.needACK.put(identifier, false);
            return identifier;
        }

        return 0;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     */
    public boolean directDataPacket(DataPacket packet) {
        return this.directDataPacket(packet, false) != -1;
    }

    public int directDataPacket(DataPacket packet, boolean needACK) {
        if (!this.connected) {
            return -1;
        }

        DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return -1;
        }

        Integer identifier = this.interfaz.putPacket(this, packet, needACK, true);

        if (needACK && identifier != null) {
            this.needACK.put(identifier, false);
            return identifier;
        }

        return 0;
    }

    public boolean sleepOn(Vector3 pos) {
        if (!this.isOnline()) {
            return false;
        }

        for (Entity p : this.level.getNearbyEntities(this.boundingBox.grow(2, 1, 2), this)) {
            if (p instanceof Player) {
                if (((Player) p).sleeping != null && pos.distance(((Player) p).sleeping) <= 0.1) {
                    return false;
                }
            }
        }

        PlayerBedEnterEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerBedEnterEvent(this, this.level.getBlock(pos)));
        if (ev.isCancelled()) {
            return false;
        }

        this.sleeping = pos.clone();
        this.teleport(new Location(pos.x + 0.5, pos.y - 0.5, pos.z + 0.5, this.yaw, this.pitch, this.level), null);

        this.setDataProperty(new PositionEntityData(DATA_PLAYER_BED_POSITION, (int) pos.x, (int) pos.y, (int) pos.z));
        this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, true);

        this.setSpawn(pos);

        this.level.sleepTicks = 60;

        return true;
    }

    public void setSpawn(Vector3 pos) {
        Level level;
        if (!(pos instanceof Position)) {
            level = this.level;
        } else {
            level = ((Position) pos).getLevel();
        }
        this.spawnPosition = new Position(pos.x, pos.y, pos.z, level);
        SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
        pk.x = (int) this.spawnPosition.x;
        pk.y = (int) this.spawnPosition.y;
        pk.z = (int) this.spawnPosition.z;
        this.dataPacket(pk);
    }

    public void stopSleep() {
        if (this.sleeping != null) {
            this.server.getPluginManager().callEvent(new PlayerBedLeaveEvent(this, this.level.getBlock(this.sleeping)));

            this.sleeping = null;
            this.setDataProperty(new PositionEntityData(DATA_PLAYER_BED_POSITION, 0, 0, 0));
            this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, false);


            this.level.sleepTicks = 0;

            AnimatePacket pk = new AnimatePacket();
            pk.eid = 0;
            pk.action = 3; //Wake up
            this.dataPacket(pk);
        }
    }

    public int getGamemode() {
        return gamemode;
    }

    public boolean setGamemode(int gamemode) {
        if (gamemode < 0 || gamemode > 3 || this.gamemode == gamemode) {
            return false;
        }

        PlayerGameModeChangeEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerGameModeChangeEvent(this, gamemode));

        if (ev.isCancelled()) {
            return false;
        }

        this.gamemode = gamemode;

        this.allowFlight = this.isCreative();
        this.inAirTicks = 0;

        if (this.isSpectator()) {
            this.keepMovement = true;
            this.despawnFromAll();
        } else {
            this.keepMovement = false;
            this.spawnToAll();
        }

        this.namedTag.putInt("playerGameType", this.gamemode);

        SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
        pk.gamemode = this.gamemode & 0x01;
        this.dataPacket(pk);
        this.sendSettings();

        if (this.gamemode == Player.SPECTATOR) {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(containerSetContentPacket);
        } else {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            containerSetContentPacket.slots = Item.getCreativeItems().stream().toArray(Item[]::new);
            this.dataPacket(containerSetContentPacket);
        }

        this.inventory.sendContents(this);
        this.inventory.sendContents(this.getViewers().values());
        this.inventory.sendHeldItem(this.hasSpawned.values());

        return true;
    }

    public void sendSettings() {
        /*
         bit mask | flag name
		0x00000001 world_immutable
		0x00000002 no_pvp
		0x00000004 no_pvm
		0x00000008 no_mvp
		0x00000010 static_time
		0x00000020 nametags_visible
		0x00000040 auto_jump
		0x00000080 allow_fly
		0x00000100 noclip
		0x00000200 ?
		0x00000400 ?
		0x00000800 ?
		0x00001000 ?
		0x00002000 ?
		0x00004000 ?
		0x00008000 ?
		0x00010000 ?
		0x00020000 ?
		0x00040000 ?
		0x00080000 ?
		0x00100000 ?
		0x00200000 ?
		0x00400000 ?
		0x00800000 ?
		0x01000000 ?
		0x02000000 ?
		0x04000000 ?
		0x08000000 ?
		0x10000000 ?
		0x20000000 ?
		0x40000000 ?
		0x80000000 ?
		*/
        int flags = 0;
        if (this.isAdventure()) {
            flags |= 0x01; //Do not allow placing/breaking blocks, adventure mode
        }

		/*if(nametags !== false){
            flags |= 0x20; //Show Nametags
		}*/

        if (this.autoJump) {
            flags |= 0x40;
        }

        if (this.allowFlight) {
            flags |= 0x80;
        }

        if (this.isSpectator()) {
            flags |= 0x100;
        }

        AdventureSettingsPacket pk = new AdventureSettingsPacket();
        pk.flags = flags;
        pk.userPermission = 0x2;
        pk.globalPermission = 0x2;
        this.dataPacket(pk);
    }

    public boolean isSurvival() {
        return (this.gamemode & 0x01) == 0;
    }

    public boolean isCreative() {
        return (this.gamemode & 0x01) > 0;
    }

    public boolean isSpectator() {
        return this.gamemode == 3;
    }

    public boolean isAdventure() {
        return (this.gamemode & 0x02) > 0;
    }

    @Override
    public Item[] getDrops() {
        if (!this.isCreative()) {
            return super.getDrops();
        }

        return new Item[0];
    }

    @Override
    public boolean setDataProperty(EntityData data) {
        if (super.setDataProperty(data)) {
            this.sendData(this, new EntityMetadata().put(this.getDataProperty(data.getId())));
            return true;
        }

        return false;
    }

    @Override
    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (!this.onGround || movX != 0 || movY != 0 || movZ != 0) {
            boolean onGround = false;

            AxisAlignedBB bb = this.boundingBox.clone();
            bb.maxY = bb.minY + 0.5;
            bb.minY -= 1;

            AxisAlignedBB realBB = this.boundingBox.clone();
            realBB.maxY = realBB.minY + 0.1;
            realBB.minY -= 0.2;

            int minX = NukkitMath.floorDouble(bb.minX);
            int minY = NukkitMath.floorDouble(bb.minY);
            int minZ = NukkitMath.floorDouble(bb.minZ);
            int maxX = NukkitMath.ceilDouble(bb.maxX);
            int maxY = NukkitMath.ceilDouble(bb.maxY);
            int maxZ = NukkitMath.ceilDouble(bb.maxZ);

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.level.getBlock(this.temporalVector.setComponents(x, y, z));

                        if (!block.canPassThrough() && block.collidesWithBB(realBB)) {
                            onGround = true;
                            break;
                        }
                    }
                }
            }

            this.onGround = onGround;
        }

        this.isCollided = this.onGround;
    }

    @Override
    protected void checkBlockCollision() {
        for (Block block : this.getBlocksAround()) {
            block.onEntityCollide(this);
        }
    }

    protected void checkNearEntities() {
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(1, 0.5, 1), this)) {
            entity.scheduleUpdate();

            if (!entity.isAlive() || !this.isAlive()) {
                continue;
            }

            if (entity instanceof EntityArrow && ((EntityArrow) entity).hadCollision) {
                ItemArrow item = new ItemArrow();
                if (this.isSurvival() && !this.inventory.canAddItem(item)) {
                    continue;
                }

                InventoryPickupArrowEvent ev;
                this.server.getPluginManager().callEvent(ev = new InventoryPickupArrowEvent(this.inventory, (EntityArrow) entity));
                if (ev.isCancelled()) {
                    continue;
                }

                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);

                pk = new TakeItemEntityPacket();
                pk.entityId = 0;
                pk.target = entity.getId();
                this.dataPacket(pk);

                this.inventory.addItem(item.clone());
                entity.kill();
            } else if (entity instanceof EntityItem) {
                if (((EntityItem) entity).getPickupDelay() <= 0) {
                    Item item = ((EntityItem) entity).getItem();

                    if (item != null) {
                        if (this.isSurvival() && !this.inventory.canAddItem(item)) {
                            continue;
                        }

                        InventoryPickupItemEvent ev;
                        this.server.getPluginManager().callEvent(ev = new InventoryPickupItemEvent(this.inventory, (EntityItem) entity));
                        if (ev.isCancelled()) {
                            continue;
                        }

                        //todo: achievement
                        /*switch (item.getId()) {
                            case Item.WOOD:
                                this.awardAchievement("mineWood");
                                break;
                            case Item.DIAMOND:
                                this.awardAchievement("diamond");
                                break;
                        }*/

                        TakeItemEntityPacket pk = new TakeItemEntityPacket();
                        pk.entityId = this.getId();
                        pk.target = entity.getId();
                        Server.broadcastPacket(entity.getViewers().values(), pk);

                        pk = new TakeItemEntityPacket();
                        pk.entityId = 0;
                        pk.target = entity.getId();
                        this.dataPacket(pk);

                        this.inventory.addItem(item.clone());
                        entity.kill();
                    }
                }
            }
        }
        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(3.5, 2, 3.5), this)) {
            entity.scheduleUpdate();

            if (!entity.isAlive() || !this.isAlive()) {
                continue;
            }
            if (entity instanceof EntityXPOrb) {
                EntityXPOrb xpOrb = (EntityXPOrb) entity;
                if (xpOrb.getPickupDelay() <= 0) {
                    int exp = xpOrb.getExp();
                    this.addExperience(exp);
                    entity.kill();
                    ClickSound sound = new ClickSound(this, (float) (new NukkitRandom().nextRange(260, 360)) / 100f);
                    this.getLevel().addSound(sound);
                    break;
                }
            }
        }
    }

    protected void processMovement(int tickDiff) {
        if (!this.isAlive() || !this.spawned || this.newPosition == null || this.teleportPosition != null) {
            return;
        }


        Vector3 newPos = this.newPosition;
        double distanceSquared = newPos.distanceSquared(this);

        boolean revert = false;

        if ((distanceSquared / ((double) (tickDiff * tickDiff))) > 100 && (newPos.y - this.y) > -5) {
            revert = true;
        } else {
            if (this.chunk == null || !this.chunk.isGenerated()) {
                BaseFullChunk chunk = this.level.getChunk((int) newPos.x >> 4, (int) newPos.z >> 4, false);
                if (chunk == null || !chunk.isGenerated()) {
                    revert = true;
                    this.nextChunkOrderRun = 0;
                } else {
                    if (this.chunk != null) {
                        this.chunk.removeEntity(this);
                    }
                    this.chunk = chunk;
                }
            }
        }

        Vector2 newPosV2 = new Vector2(newPos.x, newPos.z);
        double distance = newPosV2.distance(this.x, this.z);

        if (!revert && distanceSquared != 0) {
            double dx = newPos.x - this.x;
            double dy = newPos.y - this.y;
            double dz = newPos.z - this.z;

            this.move(dx, dy, dz);

            double diffX = this.x - newPos.x;
            double diffY = this.y - newPos.y;
            double diffZ = this.z - newPos.z;

            double yS = 0.5 + this.ySize;
            if (diffY >= -yS || diffY <= yS) {
                diffY = 0;
            }

            double diff = (diffX * diffX + diffY * diffY + diffZ * diffZ) / ((double) (tickDiff * tickDiff));

            if (this.isSurvival()) {
                if (!this.isSleeping()) {
                    if (diff > 0.0625) {
                        PlayerInvalidMoveEvent ev;
                        this.getServer().getPluginManager().callEvent(ev = new PlayerInvalidMoveEvent(this, true));
                        if (!ev.isCancelled()) {
                            revert = ev.isRevert();

                            if (revert) {
                                this.server.logger.warning(this.getServer().getLanguage().translateString("nukkit.player.invalidMove", this.getName()));
                            }
                        }
                    }
                }
            }

            if (diff > 0) {
                this.x = newPos.x;
                this.y = newPos.y;
                this.z = newPos.z;
                double radius = this.getWidth() / 2;
                this.boundingBox.setBounds(this.x - radius, this.y, this.z - radius, this.x + radius, this.y + this.getHeight(), this.z + radius);
            }
        }

        Location from = new Location(
                this.lastX,
                this.lastY,
                this.lastZ,
                this.lastYaw,
                this.lastPitch,
                this.level);
        Location to = this.getLocation();

        double delta = Math.pow(this.lastX - to.x, 2) + Math.pow(this.lastY - to.y, 2) + Math.pow(this.lastZ - to.z, 2);
        double deltaAngle = Math.abs(this.lastYaw - to.yaw) + Math.abs(this.lastPitch - to.pitch);

        if (!revert && (delta > (1 / 16) || deltaAngle > 10)) {

            boolean isFirst = this.firstMove;

            this.firstMove = false;
            this.lastX = to.x;
            this.lastY = to.y;
            this.lastZ = to.z;

            this.lastYaw = to.yaw;
            this.lastPitch = to.pitch;

            if (!isFirst) {
                PlayerMoveEvent ev = new PlayerMoveEvent(this, from, to);

                this.server.getPluginManager().callEvent(ev);

                if (!(revert = ev.isCancelled())) { //Yes, this is intended
                    if (to.distanceSquared(ev.getTo()) > 0.01) { //If plugins modify the destination
                        this.teleport(ev.getTo(), null);
                    } else {
                        this.level.addEntityMovement((int) this.x >> 4, (int) this.z >> 4, this.getId(), this.x, this.y + this.getEyeHeight(), this.z, this.yaw, this.pitch, this.yaw);
                    }
                }
            }

            if (!this.isSpectator()) {
                this.checkNearEntities();
            }

            this.speed = from.subtract(to);
        } else if (distanceSquared == 0) {
            this.speed = new Vector3(0, 0, 0);
        }

        if (!revert && (this.isFoodEnabled() || ServerProperties.difficulty == 0)) {
            if ((this.isSurvival() || this.isAdventure())/* && !this.getRiddingOn() instanceof Entity*/) {

                //UpdateFoodExpLevel
                if (distance >= 0.05) {
                    double jump = 0;
                    double swimming = this.isInsideOfWater() ? 0.015 * distance : 0;
                    if (swimming != 0) distance = 0;
                    if (this.isSprinting()) {  //Running
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.7;
                        }
                        this.getFoodData().updateFoodExpLevel(0.1 * distance + jump + swimming);
                    } else {
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.2;
                        }
                        this.getFoodData().updateFoodExpLevel(0.01 * distance + jump + swimming);
                    }
                }
            }
        }

        if (revert) {

            this.lastX = from.x;
            this.lastY = from.y;
            this.lastZ = from.z;

            this.lastYaw = from.yaw;
            this.lastPitch = from.pitch;

            this.sendPosition(from, from.yaw, from.pitch, MovePlayerPacket.MODE_RESET);
            //this.sendSettings();
            this.forceMovement = new Vector3(from.x, from.y, from.z);
        } else {
            this.forceMovement = null;
            if (distanceSquared != 0 && this.nextChunkOrderRun > 20) {
                this.nextChunkOrderRun = 20;
            }
        }

        this.newPosition = null;
    }

    @Override
    public boolean setMotion(Vector3 motion) {
        if (super.setMotion(motion)) {
            if (this.chunk != null) {
                this.level.addEntityMotion(this.chunk.getX(), this.chunk.getZ(), this.getId(), this.motionX, this.motionY, this.motionZ);
                SetEntityMotionPacket pk = new SetEntityMotionPacket();
                pk.entities = new SetEntityMotionPacket.Entry[]{new SetEntityMotionPacket.Entry(0, (float) motion.x, (float) motion.y, (float) motion.z)};
                this.dataPacket(pk);
            }

            if (this.motionY > 0) {
                //todo: check this
                this.startAirTicks = (int) ((-(Math.log(this.getGravity() / (this.getGravity() + this.getDrag() * this.motionY))) / this.getDrag()) * 2 + 5);
            }

            return true;
        }

        return false;
    }

    @Override
    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.level.addPlayerMovement(this.chunk.getX(), this.chunk.getZ(), this.id, x, y, z, yaw, pitch, this.isOnGround());
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.loggedIn) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return true;
        }

        this.messageCounter = 2;

        this.lastUpdate = currentTick;

        if (!this.isAlive() && this.spawned) {
            ++this.deadTicks;
            if (this.deadTicks >= 10) {
                this.despawnFromAll();
            }
            return true;
        }

        if (this.spawned) {
            this.processMovement(tickDiff);

            this.entityBaseTick(tickDiff);

            if (this.isOnFire() && this.lastUpdate % 10 == 0) {
                if (this.isCreative() && !this.isInsideOfFire()) {
                    this.extinguish();
                } else if (this.getLevel().isRaining()) {
                    if (this.getLevel().canBlockSeeSky(this)) {
                        this.extinguish();
                    }
                }
            }

            if (!this.isSpectator() && this.speed != null) {
                if (this.onGround) {
                    if (this.inAirTicks != 0) {
                        this.startAirTicks = 5;
                    }
                    this.inAirTicks = 0;
                    this.highestPosition = this.y;
                } else {
                    if (!this.allowFlight && this.inAirTicks > 10 && !this.isSleeping() && !this.getDataPropertyBoolean(DATA_NO_AI)) {
                        double expectedVelocity = (-this.getGravity()) / ((double) this.getDrag()) - ((-this.getGravity()) / ((double) this.getDrag())) * Math.exp(-((double) this.getDrag()) * ((double) (this.inAirTicks - this.startAirTicks)));
                        double diff = (this.speed.y - expectedVelocity) * (this.speed.y - expectedVelocity);

                        if (!this.hasEffect(Effect.JUMP) && diff > 0.6 && expectedVelocity < this.speed.y && !ServerProperties.allow_flight) {
                            if (this.inAirTicks < 100) {
                                //this.sendSettings();
                                this.setMotion(new Vector3(0, expectedVelocity, 0));
                            } else if (this.kick("Flying is not enabled on this server")) {
                                return false;
                            }
                        }
                    }

                    if (this.y > highestPosition) {
                        this.highestPosition = this.y;
                    }

                    ++this.inAirTicks;

                }

                if (this.isSurvival() || this.isAdventure()) {
                    if (this.getFoodData() != null) this.getFoodData().update(tickDiff);
                }
            }
        }

        this.checkTeleportPosition();

        return true;
    }

    public void checkNetwork() {
        if (!this.isOnline()) {
            return;
        }

        if (this.nextChunkOrderRun-- <= 0 || this.chunk == null) {
            this.orderChunks();
        }

        if (!this.loadQueue.isEmpty() || !this.spawned) {
            this.sendNextChunk();
        }

        if (!this.batchedPackets.isEmpty()) {
            for (int channel : this.batchedPackets.keySet()) {
                this.server.batchPackets(new Player[]{this}, batchedPackets.get(channel).stream().toArray(DataPacket[]::new), false);
            }
            this.batchedPackets = new TreeMap<>();
        }

    }

    public boolean canInteract(Vector3 pos, double maxDistance) {
        return this.canInteract(pos, maxDistance, 0.5);
    }

    public boolean canInteract(Vector3 pos, double maxDistance, double maxDiff) {
        if (this.distanceSquared(pos) > maxDistance * maxDistance) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        double dot = dV.dot(new Vector2(this.x, this.z));
        double dot1 = dV.dot(new Vector2(pos.x, pos.z));
        return (dot1 - dot) >= -maxDiff;
    }

    public void onPlayerPreLogin() {
        //TODO: AUTHENTICATE
        this.tryAuthenticate();
    }

    public void tryAuthenticate() {
        this.authenticateCallback(true);
    }

    public void authenticateCallback(boolean valid) {
        //TODO add more stuff after authentication is available

        if (!valid) {
            this.close("", "disconnectionScreen.invalidSession");
            return;
        }

        this.processLogin();
    }

    protected void processLogin() {
        if (isWhitelisted()) {
            this.close(this.getLeaveMessage(), "Server is white-listed");

            return;
        } else if (isBanned() || ServerInfo.banByIP.contains(this.getAddress())) {
            this.close(this.getLeaveMessage(), "You are banned");

            return;
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }
        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        for (Player p : new ArrayList<>(this.server.getOnlinePlayers().values())) {
            if (p != this && p.getName() != null && this.getName() != null && Objects.equals(p.getName().toLowerCase(), this.getName().toLowerCase())) {
                if (!p.kick("logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Logged in from another location");

                    return;
                }
            } else if (p.loggedIn && this.getUniqueId().equals(p.getUniqueId())) {
                if (!p.kick("logged in from another location")) {
                    this.close(this.getLeaveMessage(), "Logged in from another location");

                    return;
                }
            }
        }

        CompoundTag nbt = this.server.getOfflinePlayerData(this.username);
        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");

            return;
        }

        this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

        nbt.putString("NameTag", this.username);

        int exp = nbt.getInt("EXP");
        int expLevel = nbt.getInt("expLevel");
        this.setExperience(exp, expLevel);

        this.gamemode = nbt.getInt("playerGameType") & 0x03;
        if (ServerProperties.force_gamemode) {
            this.gamemode = ServerProperties.gamemode;
            nbt.putInt("playerGameType", this.gamemode);
        }

        this.allowFlight = this.isCreative();

        Level level;
        if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null) {
            this.setLevel(this.server.getDefaultLevel());
            nbt.putString("Level", this.level.getName());
            nbt.getList("Pos", DoubleTag.class)
                    .add(new DoubleTag("0", this.level.getSpawnLocation().x))
                    .add(new DoubleTag("1", this.level.getSpawnLocation().y))
                    .add(new DoubleTag("2", this.level.getSpawnLocation().z));
        } else {
            this.setLevel(level);
        }

        //todo achievement
        nbt.putLong("lastPlayed", System.currentTimeMillis() / 1000);

        if (ServerProperties.auto_save) {
            this.server.saveOfflinePlayerData(this.username, nbt, true);
        }

        ListTag<DoubleTag> posList = nbt.getList("Pos", DoubleTag.class);

        super.init(this.level.getChunk((int) posList.get(0).data >> 4, (int) posList.get(2).data >> 4, true), nbt);

        if (!this.namedTag.contains("foodLevel")) {
            this.namedTag.putInt("foodLevel", 20);
        }
        int foodLevel = this.namedTag.getInt("foodLevel");
        if (!this.namedTag.contains("FoodSaturationLevel")) {
            this.namedTag.putFloat("FoodSaturationLevel", 20);
        }
        float foodSaturationLevel = this.namedTag.getFloat("foodSaturationLevel");
        this.foodData = new PlayerFood(this, foodLevel, foodSaturationLevel);

        this.server.addOnlinePlayer(this, false);

        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());

            return;
        }

        this.loggedIn = true;

        if (this.isCreative()) {
            this.inventory.setHeldItemSlot(0);
        } else {
            this.inventory.setHeldItemSlot(this.inventory.getHotbarSlotIndex(0));
        }

        if (this.isSpectator()) this.keepMovement = true;

        PlayStatusPacket statusPacket = new PlayStatusPacket();
        statusPacket.status = PlayStatusPacket.LOGIN_SUCCESS;
        this.dataPacket(statusPacket);

        if (this.spawnPosition == null && this.namedTag.contains("SpawnLevel") && (level = this.server.getLevelByName(this.namedTag.getString("SpawnLevel"))) != null) {
            this.spawnPosition = new Position(this.namedTag.getInt("SpawnX"), this.namedTag.getInt("SpawnY"), this.namedTag.getInt("SpawnZ"), level);
        }

        Position spawnPosition = this.getSpawn();

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.seed = -1;
        startGamePacket.dimension = (byte) (getLevel().getDimension() & 0xFF);
        startGamePacket.x = (float) this.x;
        startGamePacket.y = (float) this.y;
        startGamePacket.z = (float) this.z;
        startGamePacket.spawnX = (int) spawnPosition.x;
        startGamePacket.spawnY = (int) spawnPosition.y;
        startGamePacket.spawnZ = (int) spawnPosition.z;
        startGamePacket.generator = 1; //0 old, 1 infinite, 2 flat
        startGamePacket.gamemode = this.gamemode & 0x01;
        startGamePacket.eid = 0; //Always use EntityID as zero for the actual player
        startGamePacket.b1 = true;
        startGamePacket.b2 = true;
        startGamePacket.b3 = false;
        startGamePacket.unknownstr = "";
        this.dataPacket(startGamePacket);

        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.time = this.level.getTime();
        setTimePacket.started = !this.level.stopTime;
        this.dataPacket(setTimePacket);

        SetSpawnPositionPacket setSpawnPositionPacket = new SetSpawnPositionPacket();
        setSpawnPositionPacket.x = (int) spawnPosition.x;
        setSpawnPositionPacket.y = (int) spawnPosition.y;
        setSpawnPositionPacket.z = (int) spawnPosition.z;
        this.dataPacket(setSpawnPositionPacket);

        UpdateAttributesPacket updateAttributesPacket = new UpdateAttributesPacket();
        updateAttributesPacket.entityId = 0;
        updateAttributesPacket.entries = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(this.getHealth()),
                Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(this.getMovementSpeed())
        };
        this.dataPacket(updateAttributesPacket);

        SetDifficultyPacket setDifficultyPacket = new SetDifficultyPacket();
        setDifficultyPacket.difficulty = ServerProperties.difficulty;
        this.dataPacket(setDifficultyPacket);

        this.server.logger.info(this.getServer().getLanguage().translateString("nukkit.player.logIn", new String[]{
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.ip,
                String.valueOf(this.port),
                String.valueOf(this.id),
                this.level.getName(),
                String.valueOf(NukkitMath.round(this.x, 4)),
                String.valueOf(NukkitMath.round(this.y, 4)),
                String.valueOf(NukkitMath.round(this.z, 4))
        }));

        if (this.isOp()) {
            this.setRemoveFormat(false);
        }

        if (this.gamemode == Player.SPECTATOR) {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            this.dataPacket(containerSetContentPacket);
        } else {
            ContainerSetContentPacket containerSetContentPacket = new ContainerSetContentPacket();
            containerSetContentPacket.windowid = ContainerSetContentPacket.SPECIAL_CREATIVE;
            containerSetContentPacket.slots = Item.getCreativeItems().stream().toArray(Item[]::new);
            this.dataPacket(containerSetContentPacket);
        }

        this.forceMovement = this.teleportPosition = this.getPosition();

        this.server.onPlayerLogin(this);
    }

    public void handleDataPacket(DataPacket packet) {
        if (!connected) {
            return;
        }

        if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
            this.server.getNetwork().processBatch((BatchPacket) packet, this);
            return;
        }

        DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
        this.server.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            return;
        }

        switch (packet.pid()) {
            case ProtocolInfo.LOGIN_PACKET:
                if (this.loggedIn) {
                    break;
                }

                LoginPacket loginPacket = (LoginPacket) packet;
                this.username = TextFormat.clean(loginPacket.username);
                this.displayName = this.username;
                this.setNameTag(this.username);
                this.iusername = this.username.toLowerCase();

                if (this.server.getOnlinePlayers().size() >= ServerProperties.max_players && this.kick("disconnectionScreen.serverFull", false)) {
                    break;
                }

                String message;
                boolean flag = true;
                for (int i : ProtocolInfo.ACCEPTED_PROTOCOLS) {
                    if (i == loginPacket.protocol1) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    if (loginPacket.protocol1 < ProtocolInfo.CURRENT_PROTOCOL) {
                        message = "disconnectionScreen.outdatedClient";

                        PlayStatusPacket pk = new PlayStatusPacket();
                        pk.status = PlayStatusPacket.LOGIN_FAILED_CLIENT;
                        this.directDataPacket(pk);
                    } else {
                        message = "disconnectionScreen.outdatedServer";

                        PlayStatusPacket pk = new PlayStatusPacket();
                        pk.status = PlayStatusPacket.LOGIN_FAILED_SERVER;
                        this.directDataPacket(pk);
                    }
                    this.close("", message, false);
                    break;
                }
                this.protocol = loginPacket.protocol1;
                this.randomClientId = loginPacket.clientId;

                this.uuid = loginPacket.clientUUID;
                this.rawUUID = Binary.writeUUID(this.uuid);
                this.clientSecret = loginPacket.clientSecret;

                boolean valid = true;
                int len = loginPacket.username.length();
                if (len > 16 || len < 3) {
                    valid = false;
                }

                for (int i = 0; i < len && valid; i++) {
                    char c = loginPacket.username.charAt(i);
                    if ((c >= 'a' && c <= 'z') ||
                            (c >= 'A' && c <= 'Z') ||
                            (c >= '0' && c <= '9') || c == '_'
                            ) {
                        continue;
                    }

                    valid = false;
                    break;
                }

                if (!valid || Objects.equals(this.iusername, "rcon") || Objects.equals(this.iusername, "console")) {
                    this.close("", "disconnectionScreen.invalidName");

                    break;
                }

                if (!loginPacket.skin.isValid()) {
                    this.close("", "disconnectionScreen.invalidSkin");
                    break;
                }

                this.setSkin(loginPacket.skin);

                PlayerPreLoginEvent playerPreLoginEvent;
                this.server.getPluginManager().callEvent(playerPreLoginEvent = new PlayerPreLoginEvent(this, "Plugin reason"));
                if (playerPreLoginEvent.isCancelled()) {
                    this.close("", playerPreLoginEvent.getKickMessage());

                    break;
                }

                this.onPlayerPreLogin();

                break;
            case ProtocolInfo.MOVE_PLAYER_PACKET:


                MovePlayerPacket movePlayerPacket = (MovePlayerPacket) packet;
                Vector3 newPos = new Vector3(movePlayerPacket.x, movePlayerPacket.y - this.getEyeHeight(), movePlayerPacket.z);

                boolean revert = false;
                if (!this.isAlive() || !this.spawned) {
                    revert = true;
                    this.forceMovement = new Vector3(this.x, this.y, this.z);
                }

                if (this.teleportPosition != null || this.forceMovement != null && (newPos.distanceSquared(this.forceMovement) > 0.1 || revert)) {
                    this.sendPosition(this.teleportPosition == null ? this.forceMovement : this.teleportPosition, movePlayerPacket.yaw, movePlayerPacket.pitch);
                } else {

                    movePlayerPacket.yaw %= 360;
                    movePlayerPacket.pitch %= 360;

                    if (movePlayerPacket.yaw < 0) {
                        movePlayerPacket.yaw += 360;
                    }

                    this.setRotation(movePlayerPacket.yaw, movePlayerPacket.pitch);
                    this.newPosition = newPos;
                    this.forceMovement = null;
                }

                if (riding != null) {
                    if (riding instanceof EntityBoat) {
                        riding.setPositionAndRotation(this.temporalVector.setComponents(movePlayerPacket.x, movePlayerPacket.y - 1, movePlayerPacket.z), (movePlayerPacket.headYaw + 90) % 360, 0);
                    }
                }

                break;
            case ProtocolInfo.MOB_EQUIPMENT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                MobEquipmentPacket mobEquipmentPacket = (MobEquipmentPacket) packet;

                if (mobEquipmentPacket.slot == 0x28 || mobEquipmentPacket.slot == 0 || mobEquipmentPacket.slot == 255) {
                    mobEquipmentPacket.slot = -1;
                } else {
                    mobEquipmentPacket.slot -= 9;
                }

                Item item;
                int slot;
                if (this.isCreative()) {
                    item = mobEquipmentPacket.item;
                    slot = Item.getCreativeItemIndex(item);
                } else {
                    item = this.inventory.getItem(mobEquipmentPacket.slot);
                    slot = mobEquipmentPacket.slot;
                }

                if (mobEquipmentPacket.slot == -1) {
                    if (this.isCreative()) {
                        boolean found = false;
                        for (int i = 0; i < this.inventory.getHotbarSize(); ++i) {
                            if (this.inventory.getHotbarSlotIndex(i) == -1) {
                                this.inventory.setHeldItemIndex(i);
                                found = true;
                                break;
                            }

                        }
                        if (!found) {
                            this.inventory.sendContents(this);
                            break;
                        }
                    } else {
                        if (mobEquipmentPacket.selectedSlot >= 0 && mobEquipmentPacket.selectedSlot < 9) {
                            this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                            this.inventory.setHeldItemSlot(mobEquipmentPacket.slot);
                        } else {
                            this.inventory.sendContents(this);
                            break;
                        }
                    }
                } else if (item == null || slot == -1 || !item.deepEquals(mobEquipmentPacket.item)) {
                    this.inventory.sendContents(this);
                    break;
                } else if (this.isCreative()) {
                    this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                    this.inventory.setItem(mobEquipmentPacket.selectedSlot, item);
                    this.inventory.setHeldItemSlot(mobEquipmentPacket.selectedSlot);
                } else {
                    if (mobEquipmentPacket.selectedSlot >= 0 && mobEquipmentPacket.selectedSlot < this.inventory.getHotbarSize()) {
                        this.inventory.setHeldItemIndex(mobEquipmentPacket.selectedSlot);
                        this.inventory.setHeldItemSlot(slot);
                    } else {
                        this.inventory.sendContents(this);
                        break;
                    }
                }

                this.inventory.sendHeldItem(this.hasSpawned.values());

                this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
                break;
            case ProtocolInfo.USE_ITEM_PACKET:
                if (!this.spawned || !this.isAlive() || this.blocked) {
                    break;
                }

                UseItemPacket useItemPacket = (UseItemPacket) packet;
                useItemPacket.decode(this.protocol);

                Vector3 blockVector = new Vector3(useItemPacket.x, useItemPacket.y, useItemPacket.z);

                this.craftingType = 0;

                if (useItemPacket.face >= 0 && useItemPacket.face <= 5) {
                    this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);

                    if (!this.canInteract(blockVector.add(0.5, 0.5, 0.5), 13)) {
                    } else if (this.isCreative()) {
                        Item i = this.inventory.getItemInHand();
                        if (this.level.useItemOn(blockVector, i, useItemPacket.face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, this) != null) {
                            break;
                        }
                    } else if (!this.inventory.getItemInHand().deepEquals(useItemPacket.item)) {
                        this.inventory.sendHeldItem(this);
                    } else {
                        item = this.inventory.getItemInHand();
                        Item oldItem = item.clone();
                        //TODO: Implement adventure mode checks
                        if ((item = this.level.useItemOn(blockVector, item, useItemPacket.face, useItemPacket.fx, useItemPacket.fy, useItemPacket.fz, this)) != null) {
                            if (!item.deepEquals(oldItem) || item.getCount() != oldItem.getCount()) {
                                this.inventory.setItemInHand(item);
                                this.inventory.sendHeldItem(this.hasSpawned.values());
                            }
                            break;
                        }
                    }

                    this.inventory.sendHeldItem(this);

                    if (blockVector.distanceSquared(this) > 10000) {
                        break;
                    }

                    Block target = this.level.getBlock(blockVector);
                    Block block = target.getSide(useItemPacket.face);
                    this.level.sendBlocks(new Player[]{this}, new Block[]{target, block}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
                    break;
                } else if (useItemPacket.face == 0xff) {
                    Vector3 aimPos = (new Vector3(useItemPacket.x / 32768, useItemPacket.y / 32768, useItemPacket.z / 32768)).normalize();

                    if (this.isCreative()) {
                        item = this.inventory.getItemInHand();
                    } else if (!this.inventory.getItemInHand().deepEquals(useItemPacket.item)) {
                        this.inventory.sendHeldItem(this);
                        break;
                    } else {
                        item = this.inventory.getItemInHand();
                    }

                    PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, item, aimPos, useItemPacket.face, PlayerInteractEvent.RIGHT_CLICK_AIR);

                    this.server.getPluginManager().callEvent(playerInteractEvent);

                    if (playerInteractEvent.isCancelled()) {
                        this.inventory.sendHeldItem(this);
                        break;
                    }

                    if (item.getId() == Item.SNOWBALL) {
                        CompoundTag nbt = new CompoundTag()
                                .putList(new ListTag<DoubleTag>("Pos")
                                        .add(new DoubleTag("", x))
                                        .add(new DoubleTag("", y + this.getEyeHeight()))
                                        .add(new DoubleTag("", z)))
                                .putList(new ListTag<DoubleTag>("Motion")
                                       /* .add(new DoubleTag("", aimPos.x))
                                        .add(new DoubleTag("", aimPos.y))
                                        .add(new DoubleTag("", aimPos.z)))*/
                                        .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                .putList(new ListTag<FloatTag>("Rotation")
                                        .add(new FloatTag("", (float) yaw))
                                        .add(new FloatTag("", (float) pitch)));

                        float f = 1.5f;
                        EntitySnowball snowball = new EntitySnowball(this.chunk, nbt, this);

                        snowball.setMotion(snowball.getMotion().multiply(f));
                        if (this.isSurvival()) {
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                        }
                        ProjectileLaunchEvent projectileLaunchEvent = new ProjectileLaunchEvent(snowball);
                        this.server.getPluginManager().callEvent(projectileLaunchEvent);
                        if (projectileLaunchEvent.isCancelled()) {
                            snowball.kill();
                        } else {
                            snowball.spawnToAll();
                            this.level.addSound(new LaunchSound(this), this.getViewers().values());
                        }
                    } else if (item.getId() == Item.EGG) {
                        CompoundTag nbt = new CompoundTag()
                                .putList(new ListTag<DoubleTag>("Pos")
                                        .add(new DoubleTag("", x))
                                        .add(new DoubleTag("", y + this.getEyeHeight()))
                                        .add(new DoubleTag("", z)))
                                .putList(new ListTag<DoubleTag>("Motion")
                                       /* .add(new DoubleTag("", aimPos.x))
                                        .add(new DoubleTag("", aimPos.y))
                                        .add(new DoubleTag("", aimPos.z)))*/
                                        .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                .putList(new ListTag<FloatTag>("Rotation")
                                        .add(new FloatTag("", (float) yaw))
                                        .add(new FloatTag("", (float) pitch)));

                        float f = 1.5f;
                        EntityEgg egg = new EntityEgg(this.chunk, nbt, this);

                        egg.setMotion(egg.getMotion().multiply(f));
                        if (this.isSurvival()) {
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                        }
                        ProjectileLaunchEvent projectileLaunchEvent = new ProjectileLaunchEvent(egg);
                        this.server.getPluginManager().callEvent(projectileLaunchEvent);
                        if (projectileLaunchEvent.isCancelled()) {
                            egg.kill();
                        } else {
                            egg.spawnToAll();
                            this.level.addSound(new LaunchSound(this), this.getViewers().values());
                        }
                    } else if (item.getId() == Item.EXPERIENCE_BOTTLE) {
                        CompoundTag nbt = new CompoundTag()
                                .putList(new ListTag<DoubleTag>("Pos")
                                        .add(new DoubleTag("", x))
                                        .add(new DoubleTag("", y + this.getEyeHeight()))
                                        .add(new DoubleTag("", z)))
                                .putList(new ListTag<DoubleTag>("Motion")
                                        .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                .putList(new ListTag<FloatTag>("Rotation")
                                        .add(new FloatTag("", (float) yaw))
                                        .add(new FloatTag("", (float) pitch)))
                                .putInt("Potion", item.getDamage());
                        double f = 1.5;
                        EntityProjectile bottle = new EntityExpBottle(this.chunk, nbt, this);
                        bottle.setMotion(bottle.getMotion().multiply(f));
                        if (this.isSurvival()) {
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                        }
                        ProjectileLaunchEvent projectileEv = new ProjectileLaunchEvent(bottle);
                        this.server.getPluginManager().callEvent(projectileEv);
                        if (projectileEv.isCancelled()) {
                            bottle.kill();
                        } else {
                            bottle.spawnToAll();
                            this.level.addSound(new LaunchSound(this), this.getViewers().values());
                        }
                    } else if (item.getId() == Item.SPLASH_POTION) {
                        CompoundTag nbt = new CompoundTag()
                                .putList(new ListTag<DoubleTag>("Pos")
                                        .add(new DoubleTag("", x))
                                        .add(new DoubleTag("", y + this.getEyeHeight()))
                                        .add(new DoubleTag("", z)))
                                .putList(new ListTag<DoubleTag>("Motion")
                                        .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                .putList(new ListTag<FloatTag>("Rotation")
                                        .add(new FloatTag("", (float) yaw))
                                        .add(new FloatTag("", (float) pitch)))
                                .putShort("PotionId", item.getDamage());
                        double f = 1.5;
                        EntityPotion bottle = new EntityPotion(this.chunk, nbt, this);
                        bottle.setMotion(bottle.getMotion().multiply(f));
                        if (this.isSurvival()) {
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItemInHand(item.getCount() > 0 ? item : Item.get(Item.AIR));
                        }
                        ProjectileLaunchEvent projectileEv = new ProjectileLaunchEvent(bottle);
                        this.server.getPluginManager().callEvent(projectileEv);
                        if (projectileEv.isCancelled()) {
                            bottle.kill();
                        } else {
                            bottle.spawnToAll();
                            this.level.addSound(new LaunchSound(this), this.getViewers().values());
                        }
                    }

                    this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, true);
                    this.startAction = this.server.getTick();
                }
                break;
            case ProtocolInfo.PLAYER_ACTION_PACKET:
                if (!this.spawned || this.blocked || (!this.isAlive() && ((PlayerActionPacket) packet).action != PlayerActionPacket.ACTION_RESPAWN && ((PlayerActionPacket) packet).action != PlayerActionPacket.ACTION_DIMENSION_CHANGE)) {
                    break;
                }

                ((PlayerActionPacket) packet).entityId = this.id;
                Vector3 pos = new Vector3(((PlayerActionPacket) packet).x, ((PlayerActionPacket) packet).y, ((PlayerActionPacket) packet).z);

                switch (((PlayerActionPacket) packet).action) {
                    case PlayerActionPacket.ACTION_START_BREAK:
                        if (this.lastBreak != Long.MAX_VALUE || pos.distanceSquared(this) > 10000) {
                            break;
                        }
                        Block target = this.level.getBlock(pos);
                        PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, this.inventory.getItemInHand(), target, ((PlayerActionPacket) packet).face, target.getId() == 0 ? PlayerInteractEvent.LEFT_CLICK_AIR : PlayerInteractEvent.LEFT_CLICK_BLOCK);
                        this.getServer().getPluginManager().callEvent(playerInteractEvent);
                        if (playerInteractEvent.isCancelled()) {
                            this.inventory.sendHeldItem(this);
                            break;
                        }
                        Block block = target.getSide(((PlayerActionPacket) packet).face);
                        if (block.getId() == Block.FIRE) {
                            this.level.setBlock(block, new BlockAir(), true);
                        }
                        this.lastBreak = System.currentTimeMillis();
                        break;

                    case PlayerActionPacket.ACTION_ABORT_BREAK:
                        this.lastBreak = Long.MAX_VALUE;
                        break;

                    case PlayerActionPacket.ACTION_RELEASE_ITEM:
                        if (this.startAction > -1 && this.getDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION)) {
                            if (this.inventory.getItemInHand().getId() == Item.BOW) {

                                Item bow = this.inventory.getItemInHand();
                                ItemArrow itemArrow = new ItemArrow();
                                if (this.isSurvival() && !this.inventory.contains(itemArrow)) {
                                    this.inventory.sendContents(this);
                                    break;
                                }

                                CompoundTag nbt = new CompoundTag()
                                        .putList(new ListTag<DoubleTag>("Pos")
                                                .add(new DoubleTag("", x))
                                                .add(new DoubleTag("", y + this.getEyeHeight()))
                                                .add(new DoubleTag("", z)))
                                        .putList(new ListTag<DoubleTag>("Motion")
                                                .add(new DoubleTag("", -Math.sin(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                                                .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                                                .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                                        .putList(new ListTag<FloatTag>("Rotation")
                                                .add(new FloatTag("", (float) yaw))
                                                .add(new FloatTag("", (float) pitch)))
                                        .putShort("Fire", this.isOnFire() ? 45 * 60 : 0);

                                int diff = (this.server.getTick() - this.startAction);
                                double p = (double) diff / 20;

                                double f = Math.min((p * p + p * 2) / 3, 1) * 2;
                                EntityShootBowEvent entityShootBowEvent = new EntityShootBowEvent(this, bow, new EntityArrow(this.chunk, nbt, this, f == 2), f);

                                if (f < 0.1 || diff < 5) {
                                    entityShootBowEvent.setCancelled();
                                }

                                this.server.getPluginManager().callEvent(entityShootBowEvent);
                                if (entityShootBowEvent.isCancelled()) {
                                    entityShootBowEvent.getProjectile().kill();
                                    this.inventory.sendContents(this);
                                } else {
                                    entityShootBowEvent.getProjectile().setMotion(entityShootBowEvent.getProjectile().getMotion().multiply(entityShootBowEvent.getForce()));
                                    if (this.isSurvival()) {
                                        this.inventory.removeItem(itemArrow);
                                        bow.setDamage(bow.getDamage() + 1);
                                        if (bow.getDamage() >= 385) {
                                            this.inventory.setItemInHand(new ItemBlock(new BlockAir(), 0, 0));
                                        } else {
                                            this.inventory.setItemInHand(bow);
                                        }
                                    }
                                    if (entityShootBowEvent.getProjectile() != null) {
                                        ProjectileLaunchEvent projectev = new ProjectileLaunchEvent(entityShootBowEvent.getProjectile());
                                        this.server.getPluginManager().callEvent(projectev);
                                        if (projectev.isCancelled()) {
                                            entityShootBowEvent.getProjectile().kill();
                                        } else {
                                            entityShootBowEvent.getProjectile().spawnToAll();
                                            this.level.addSound(new LaunchSound(this), this.getViewers().values());
                                        }
                                    } else {
                                        entityShootBowEvent.getProjectile().spawnToAll();
                                    }
                                }
                            }
                        }
                        //milk removed here, see the section of food

                    case PlayerActionPacket.ACTION_STOP_SLEEPING:
                        this.stopSleep();
                        break;

                    case PlayerActionPacket.ACTION_RESPAWN:
                        if (!this.spawned || this.isAlive() || !this.isOnline()) {
                            break;
                        }

                        if (ServerProperties.hardcore) {
                            this.setBanned(true);
                            break;
                        }

                        this.craftingType = 0;

                        PlayerRespawnEvent playerRespawnEvent = new PlayerRespawnEvent(this, this.getSpawn());
                        this.server.getPluginManager().callEvent(playerRespawnEvent);

                        this.teleport(playerRespawnEvent.getRespawnPosition(), null);

                        this.setSprinting(false);
                        this.setSneaking(false);

                        this.extinguish();
                        this.setDataProperty(new ShortEntityData(Player.DATA_AIR, 300));
                        this.deadTicks = 0;
                        this.noDamageTicks = 60;

                        this.setHealth(this.getMaxHealth());
                        this.getFoodData().setLevel(20, 20);

                        this.removeAllEffects();
                        this.sendData(this);

                        this.setMovementSpeed(0.1f);

                        this.sendSettings();
                        this.inventory.sendContents(this);
                        this.inventory.sendArmorContents(this);

                        this.blocked = false;

                        this.spawnToAll();
                        this.scheduleUpdate();
                        break;

                    case PlayerActionPacket.ACTION_START_SPRINT:
                        PlayerToggleSprintEvent playerToggleSprintEvent = new PlayerToggleSprintEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                        if (playerToggleSprintEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSprinting(true);
                        }
                        break;

                    case PlayerActionPacket.ACTION_STOP_SPRINT:
                        playerToggleSprintEvent = new PlayerToggleSprintEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                        if (playerToggleSprintEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSprinting(false);
                        }
                        break;

                    case PlayerActionPacket.ACTION_START_SNEAK:
                        PlayerToggleSneakEvent playerToggleSneakEvent = new PlayerToggleSneakEvent(this, true);
                        this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                        if (playerToggleSneakEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSneaking(true);
                        }
                        break;

                    case PlayerActionPacket.ACTION_STOP_SNEAK:
                        playerToggleSneakEvent = new PlayerToggleSneakEvent(this, false);
                        this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                        if (playerToggleSneakEvent.isCancelled()) {
                            this.sendData(this);
                        } else {
                            this.setSneaking(false);
                        }
                        break;
                }

                this.startAction = -1;
                this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
                break;

            case ProtocolInfo.REMOVE_BLOCK_PACKET:
                if (!this.spawned || this.blocked || !this.isAlive()) {
                    break;
                }
                this.craftingType = 0;

                Vector3 vector = new Vector3(((RemoveBlockPacket) packet).x, ((RemoveBlockPacket) packet).y, ((RemoveBlockPacket) packet).z);

                if (this.isCreative()) {
                    item = this.inventory.getItemInHand();
                } else {
                    item = this.inventory.getItemInHand();
                }

                Item oldItem = item.clone();

                if (this.canInteract(vector.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 6) && (item = this.level.useBreakOn(vector, item, this, true)) != null) {
                    if (this.isSurvival()) {
                        this.getFoodData().updateFoodExpLevel(0.025);
                        if (!item.deepEquals(oldItem) || item.getCount() != oldItem.getCount()) {
                            this.inventory.setItemInHand(item);
                            this.inventory.sendHeldItem(this.hasSpawned.values());
                        }
                    }
                    break;
                }

                this.inventory.sendContents(this);
                Block target = this.level.getBlock(vector);
                BlockEntity blockEntity = this.level.getBlockEntity(vector);

                this.level.sendBlocks(new Player[]{this}, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

                this.inventory.sendHeldItem(this);

                if (blockEntity instanceof BlockEntitySpawnable) {
                    ((BlockEntitySpawnable) blockEntity).spawnTo(this);
                }
                break;

            case ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET:
                break;

            case ProtocolInfo.INTERACT_PACKET:
                if (!this.spawned || !this.isAlive() || this.blocked) {
                    break;
                }
                this.craftingType = 0;
                Entity targetEntity = this.level.getEntity(((InteractPacket) packet).target);
                boolean cancelled = false;
                if (targetEntity instanceof Player && !ServerProperties.pvp) {
                    cancelled = true;
                }

                if (targetEntity != null && this.isAlive() && targetEntity.isAlive()) {
                    if (this.getGamemode() == Player.VIEW) {
                        cancelled = true;
                    }
                    if (targetEntity instanceof EntityItem || targetEntity instanceof EntityArrow) {
                        this.kick("Attempting to attack an invalid entity");
                        this.server.logger.warning(this.getServer().getLanguage().translateString("nukkit.player.invalidEntity", this.getName()));
                        break;
                    }

                    item = this.inventory.getItemInHand();
                    HashMap<Integer, Float> damageTable = new HashMap<Integer, Float>() {
                        {
                            put(Item.WOODEN_SWORD, 4f);
                            put(Item.GOLD_SWORD, 4f);
                            put(Item.STONE_SWORD, 5f);
                            put(Item.IRON_SWORD, 6f);
                            put(Item.DIAMOND_SWORD, 7f);
                            put(Item.WOODEN_AXE, 3f);
                            put(Item.GOLD_AXE, 3f);
                            put(Item.STONE_AXE, 3f);
                            put(Item.IRON_AXE, 5f);
                            put(Item.DIAMOND_AXE, 6f);
                            put(Item.WOODEN_PICKAXE, 2f);
                            put(Item.GOLD_PICKAXE, 2f);
                            put(Item.STONE_PICKAXE, 3f);
                            put(Item.IRON_PICKAXE, 4f);
                            put(Item.DIAMOND_PICKAXE, 5f);
                            put(Item.WOODEN_SHOVEL, 1f);
                            put(Item.GOLD_SHOVEL, 1f);
                            put(Item.STONE_SHOVEL, 2f);
                            put(Item.IRON_SHOVEL, 3f);
                            put(Item.DIAMOND_SHOVEL, 4f);
                        }
                    };

                    HashMap<Integer, Float> damage = new HashMap<>();
                    damage.put(EntityDamageEvent.MODIFIER_BASE, damageTable.getOrDefault(item.getId(), 1f));

                    if (!this.canInteract(targetEntity, 8)) {
                        cancelled = true;
                    } else if (targetEntity instanceof Player) {
                        if ((((Player) targetEntity).getGamemode() & 0x01) > 0) {
                            break;
                        } else if (!ServerProperties.pvp || ServerProperties.difficulty == 0) {
                            cancelled = true;
                        }

                        HashMap<Integer, Float> armorValues = new HashMap<Integer, Float>() {
                            {
                                put(Item.LEATHER_CAP, 1f);
                                put(Item.LEATHER_TUNIC, 3f);
                                put(Item.LEATHER_PANTS, 2f);
                                put(Item.LEATHER_BOOTS, 1f);
                                put(Item.CHAIN_HELMET, 1f);
                                put(Item.CHAIN_CHESTPLATE, 5f);
                                put(Item.CHAIN_LEGGINGS, 4f);
                                put(Item.CHAIN_BOOTS, 1f);
                                put(Item.GOLD_HELMET, 1f);
                                put(Item.GOLD_CHESTPLATE, 5f);
                                put(Item.GOLD_LEGGINGS, 3f);
                                put(Item.GOLD_BOOTS, 1f);
                                put(Item.IRON_HELMET, 2f);
                                put(Item.IRON_CHESTPLATE, 6f);
                                put(Item.IRON_LEGGINGS, 5f);
                                put(Item.IRON_BOOTS, 2f);
                                put(Item.DIAMOND_HELMET, 3f);
                                put(Item.DIAMOND_CHESTPLATE, 8f);
                                put(Item.DIAMOND_LEGGINGS, 6f);
                                put(Item.DIAMOND_BOOTS, 3f);
                            }
                        };

                        float points = 0;
                        for (Item i : ((Player) targetEntity).getInventory().getArmorContents()) {
                            points += armorValues.getOrDefault(i.getId(), 0f);
                        }

                        damage.put(EntityDamageEvent.MODIFIER_ARMOR, (float) (damage.getOrDefault(EntityDamageEvent.MODIFIER_ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.MODIFIER_BASE, 1f) * points * 0.04)));
                    } else if (targetEntity instanceof EntityVehicle) {
                        SetEntityLinkPacket pk;
                        switch (((InteractPacket) packet).action) {
                            case InteractPacket.ACTION_RIGHT_CLICK:
                                cancelled = true;

                                if (((EntityVehicle) targetEntity).linkedEntity != null) {
                                    break;
                                }
                                pk = new SetEntityLinkPacket();
                                pk.rider = targetEntity.getId();
                                pk.riding = this.id;
                                pk.type = 2;
                                Server.broadcastPacket(this.hasSpawned.values(), pk);

                                pk = new SetEntityLinkPacket();
                                pk.rider = targetEntity.getId();
                                pk.riding = 0;
                                pk.type = 2;
                                dataPacket(pk);

                                riding = targetEntity;
                                ((EntityVehicle) targetEntity).linkedEntity = this;

                                this.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, true);
                                break;
                            case InteractPacket.ACTION_VEHICLE_EXIT:
                                pk = new SetEntityLinkPacket();
                                pk.rider = targetEntity.getId();
                                pk.riding = this.id;
                                pk.type = 3;
                                Server.broadcastPacket(this.hasSpawned.values(), pk);

                                pk = new SetEntityLinkPacket();
                                pk.rider = targetEntity.getId();
                                pk.riding = 0;
                                pk.type = 3;
                                dataPacket(pk);

                                cancelled = true;
                                riding = null;
                                ((EntityVehicle) targetEntity).linkedEntity = null;
                                this.setDataFlag(DATA_FLAGS, DATA_FLAG_RIDING, false);
                                break;
                        }
                    }

                    EntityDamageByEntityEvent entityDamageByEntityEvent = new EntityDamageByEntityEvent(this, targetEntity, EntityDamageEvent.CAUSE_ENTITY_ATTACK, damage);
                    if (cancelled) {
                        entityDamageByEntityEvent.setCancelled();
                    }

                    targetEntity.attack(entityDamageByEntityEvent);

                    if (entityDamageByEntityEvent.isCancelled()) {
                        if (item.isTool() && this.isSurvival()) {
                            this.inventory.sendContents(this);
                        }
                        break;
                    }

                    if (item.isTool() && this.isSurvival()) {
                        if (item.useOn(targetEntity) && item.getDamage() >= item.getMaxDurability()) {
                            this.inventory.setItemInHand(new ItemBlock(new BlockAir()));
                        } else {
                            this.inventory.setItemInHand(item);
                        }
                    }
                }

                break;
            case ProtocolInfo.ANIMATE_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                PlayerAnimationEvent animationEvent = new PlayerAnimationEvent(this, ((AnimatePacket) packet).action);
                this.server.getPluginManager().callEvent(animationEvent);
                if (animationEvent.isCancelled()) {
                    break;
                }

                AnimatePacket animatePacket = new AnimatePacket();
                animatePacket.eid = this.getId();
                animatePacket.action = animationEvent.getAnimationType();
                Server.broadcastPacket(this.getViewers().values(), animatePacket);
                break;
            case ProtocolInfo.SET_HEALTH_PACKET:
                //use UpdateAttributePacket instead
                break;

            case ProtocolInfo.ENTITY_EVENT_PACKET:
                if (!this.spawned || this.blocked || !this.isAlive()) {
                    break;
                }
                this.craftingType = 0;

                this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false); //TODO: check if this should be true
                EntityEventPacket entityEventPacket = (EntityEventPacket) packet;

                switch (entityEventPacket.event) {
                    case EntityEventPacket.USE_ITEM: //Eating
                        Item itemInHand = this.inventory.getItemInHand();
                        PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(this, itemInHand);
                        this.server.getPluginManager().callEvent(consumeEvent);
                        if (consumeEvent.isCancelled()) {
                            this.inventory.sendContents(this);
                            break;
                        }

                        if (itemInHand.getId() == Item.POTION) {
                            Potion potion = Potion.getPotion(itemInHand.getDamage()).setSplash(false);

                            if (this.getGamemode() == SURVIVAL) {
                                if (itemInHand.getCount() > 1) {
                                    ItemGlassBottle bottle = new ItemGlassBottle();
                                    if (this.inventory.canAddItem(bottle)) {
                                        this.inventory.addItem(bottle);
                                    }
                                    --itemInHand.count;
                                } else {
                                    itemInHand = new ItemGlassBottle();
                                }
                            }

                            if (potion != null) {
                                potion.applyPotion(this);
                            }

                        } else {
                            EntityEventPacket pk = new EntityEventPacket();
                            pk.eid = this.getId();
                            pk.event = EntityEventPacket.USE_ITEM;
                            this.dataPacket(pk);
                            Server.broadcastPacket(this.getViewers().values(), pk);

                            Food food = Food.getByRelative(itemInHand);
                            if (food != null) if (food.eatenBy(this)) --itemInHand.count;

                        }

                        this.inventory.setItemInHand(itemInHand);
                        this.inventory.sendHeldItem(this);

                        break;
                }
                break;
            case ProtocolInfo.DROP_ITEM_PACKET:
                if (!this.spawned || this.blocked || !this.isAlive()) {
                    break;
                }
                DropItemPacket dropItem = (DropItemPacket) packet;

                if (dropItem.item.getId() <= 0) {
                    break;
                }

                item = this.inventory.contains(dropItem.item) ? dropItem.item : this.inventory.getItemInHand();
                PlayerDropItemEvent dropItemEvent = new PlayerDropItemEvent(this, item);
                this.server.getPluginManager().callEvent(dropItemEvent);
                if (dropItemEvent.isCancelled()) {
                    this.inventory.sendContents(this);
                    break;
                }

                this.inventory.removeItem(item);
                Vector3 motion = this.getDirectionVector().multiply(0.4);

                this.level.dropItem(this.add(0, 1.3, 0), item, motion, 40);

                this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);
                break;

            case ProtocolInfo.TEXT_PACKET:
                if (!this.spawned || !this.isAlive()) {
                    break;
                }

                this.craftingType = 0;
                TextPacket textPacket = (TextPacket) packet;

                if (textPacket.type == TextPacket.TYPE_CHAT) {
                    textPacket.message = this.removeFormat ? TextFormat.clean(textPacket.message) : textPacket.message;
                    for (String msg : textPacket.message.split("\n")) {
                        if (!msg.trim().isEmpty() && msg.length() <= 255 && this.messageCounter-- > 0) {
                            if (msg.startsWith("/")) { //Command
                                PlayerCommandPreprocessEvent commandPreprocessEvent = new PlayerCommandPreprocessEvent(this, msg);
                                if (commandPreprocessEvent.getMessage().length() > 320) {
                                    commandPreprocessEvent.setCancelled();
                                }
                                this.server.getPluginManager().callEvent(commandPreprocessEvent);
                                if (commandPreprocessEvent.isCancelled()) {
                                    break;
                                }
                                //Timings::playerCommandTimer.startTiming();
                                this.server.dispatchCommand(commandPreprocessEvent.getPlayer(), commandPreprocessEvent.getMessage().substring(1));
                                //Timings::playerCommandTimer.stopTiming();
                            } else { //Chat
                                PlayerChatEvent chatEvent = new PlayerChatEvent(this, msg);
                                this.server.getPluginManager().callEvent(chatEvent);
                                if (!chatEvent.isCancelled()) {
                                    this.server.broadcastMessage(this.getServer().getLanguage().translateString(chatEvent.getFormat(), new String[]{chatEvent.getPlayer().getDisplayName(), chatEvent.getMessage()}), chatEvent.getRecipients());
                                }
                            }
                        }
                    }
                }
                break;
            case ProtocolInfo.CONTAINER_CLOSE_PACKET:
                ContainerClosePacket containerClosePacket = (ContainerClosePacket) packet;
                if (!this.spawned || containerClosePacket.windowid == 0) {
                    break;
                }
                this.craftingType = 0;
                this.currentTransaction = null;
                if (this.windowIndex.containsKey(containerClosePacket.windowid)) {
                    this.server.getPluginManager().callEvent(new InventoryCloseEvent(this.windowIndex.get(containerClosePacket.windowid), this));
                    this.removeWindow(this.windowIndex.get(containerClosePacket.windowid));
                } else {
                    this.windowIndex.remove(containerClosePacket.windowid);
                }
                break;

            case ProtocolInfo.CRAFTING_EVENT_PACKET:
                CraftingEventPacket craftingEventPacket = (CraftingEventPacket) packet;
                if (!this.spawned || !this.isAlive()) {
                    break;
                } else if (!this.windowIndex.containsKey(craftingEventPacket.windowId)) {
                    this.inventory.sendContents(this);
                    containerClosePacket = new ContainerClosePacket();
                    containerClosePacket.windowid = craftingEventPacket.windowId;
                    this.dataPacket(containerClosePacket);
                    break;
                }

                Recipe recipe = this.server.getCraftingManager().getRecipe(craftingEventPacket.id);

                if ((recipe == null) || (((recipe instanceof BigShapelessRecipe) || (recipe instanceof BigShapedRecipe)) && this.craftingType == 0)) {
                    this.inventory.sendContents(this);
                    break;
                }

                for (int i = 0; i < craftingEventPacket.input.length; i++) {
                    Item inputItem = craftingEventPacket.input[i];
                    if (inputItem.getDamage() == -1 || inputItem.getDamage() == 0xffff) {
                        inputItem.setDamage(null);
                    }

                    if (i < 9 && inputItem.getId() > 0) {
                        inputItem.setCount(1);
                    }
                }

                boolean canCraft = true;

                if (recipe instanceof ShapedRecipe) {
                    int offsetX = 0;
                    int offsetY = 0;

                    if (this.craftingType == 1) {
                        int minX = -1, minY = -1, maxX = 0, maxY = 0;
                        for (int x = 0; x < 3 && canCraft; ++x) {
                            for (int y = 0; y < 3; ++y) {
                                Item readItem = craftingEventPacket.input[y * 3 + x];
                                if (readItem.getId() != Item.AIR) {
                                    if (minY == -1 || minY > y) {
                                        minY = y;
                                    }
                                    if (maxY < y) {
                                        maxY = y;
                                    }
                                    if (minX == -1) {
                                        minX = x;
                                    }
                                    if (maxX < x) {
                                        maxX = x;
                                    }
                                }
                            }
                        }
                        if (maxX == minX) {
                            offsetX = minX;
                        }
                        if (maxY == minY) {
                            offsetY = minY;
                        }
                    }

                    //To fix some items can't craft
                    for (int x = 0; x < 3 - offsetX && canCraft; ++x) {
                        for (int y = 0; y < 3 - offsetY; ++y) {
                            item = craftingEventPacket.input[(y + offsetY) * 3 + (x + offsetX)];
                            Item ingredient = ((ShapedRecipe) recipe).getIngredient(x, y);
                            //todo: check this https://github.com/PocketMine/PocketMine-MP/commit/58709293cf4eee2e836a94226bbba4aca0f53908
                            if (item.getCount() > 0) {
                                if (ingredient == null || !ingredient.deepEquals(item, ingredient.hasMeta(), ingredient.getCompoundTag() != null)) {
                                    canCraft = false;
                                    break;
                                }

                            }
                        }
                    }

                    //If can't craft by auto resize, will try to craft this item in another way
                    if (!canCraft) {
                        canCraft = true;
                        for (int x = 0; x < 3 && canCraft; ++x) {
                            for (int y = 0; y < 3; ++y) {
                                item = craftingEventPacket.input[y * 3 + x];
                                Item ingredient = ((ShapedRecipe) recipe).getIngredient(x, y);
                                if (item.getCount() > 0) {
                                    if (ingredient == null || !ingredient.deepEquals(item, ingredient.hasMeta(), ingredient.getCompoundTag() != null)) {
                                        canCraft = false;
                                        break;
                                    }

                                }
                            }
                        }
                    }

                } else if (recipe instanceof ShapelessRecipe) {
                    List<Item> needed = ((ShapelessRecipe) recipe).getIngredientList();

                    for (int x = 0; x < 3 && canCraft; ++x) {
                        for (int y = 0; y < 3; ++y) {
                            item = craftingEventPacket.input[y * 3 + x].clone();

                            for (Item n : new ArrayList<>(needed)) {
                                if (n.deepEquals(item, n.hasMeta(), n.getCompoundTag() != null)) {
                                    int remove = Math.min(n.getCount(), item.getCount());
                                    n.setCount(n.getCount() - remove);
                                    item.setCount(item.getCount() - remove);

                                    if (n.getCount() == 0) {
                                        needed.remove(n);
                                    }
                                }
                            }

                            if (item.getCount() > 0) {
                                canCraft = false;
                                break;
                            }
                        }
                    }

                    if (!needed.isEmpty()) {
                        canCraft = false;
                    }
                } else {
                    canCraft = false;
                }

                List<Item> ingredientsList = new ArrayList<>();
                if (recipe instanceof ShapedRecipe) {
                    for (int x = 0; x < 3; x++) {
                        for (int y = 0; y < 3; y++) {
                            Item need = ((ShapedRecipe) recipe).getIngredient(x, y);
                            if (need.getId() == 0) {
                                continue;
                            }
                            for (int count = need.getCount(); count > 0; count--) {
                                Item needAdd = need.clone();
                                //todo: check if there need to set item's count to 1, I'm too tired to check that today =w=
                                needAdd.setCount(1);
                                ingredientsList.add(needAdd);
                            }
                        }
                    }
                }
                if (recipe instanceof ShapelessRecipe) {
                    List<Item> recipeItem = ((ShapelessRecipe) recipe).getIngredientList();
                    for (Item need : recipeItem) {
                        if (need.getId() == 0) {
                            continue;
                        }
                        Item needAdd = need.clone();
                        //todo: check if there need to set item's count to 1, I'm too tired to check that today =w=
                        needAdd.setCount(1);
                        ingredientsList.add(needAdd);
                    }
                }

                Item[] ingredients = ingredientsList.stream().toArray(Item[]::new);

                Item result = craftingEventPacket.output[0];

                if (!canCraft || !recipe.getResult().deepEquals(result)) {
                    this.server.logger.debug("Unmatched recipe " + recipe.getId() + " from player " + this.getName() + ": expected " + recipe.getResult() + ", got " + result + ", using: " + Arrays.asList(ingredients).toString());
                    this.inventory.sendContents(this);
                    break;
                }

                int[] used = new int[this.inventory.getSize()];

                for (Item ingredient : ingredients) {
                    slot = -1;
                    for (int index : this.inventory.getContents().keySet()) {
                        Item i = this.inventory.getContents().get(index);
                        if (ingredient.getId() != 0 && ingredient.deepEquals(i, ingredient.hasMeta()) && (i.getCount() - used[index]) >= 1) {
                            slot = index;
                            used[index]++;
                            break;
                        }
                    }

                    if (ingredient.getId() != 0 && slot == -1) {
                        canCraft = false;
                        break;
                    }
                }

                if (!canCraft) {
                    this.server.logger.debug("Unmatched recipe " + recipe.getId() + " from player " + this.getName() + ": client does not have enough items, using: " + Arrays.asList(ingredients).toString());
                    this.inventory.sendContents(this);
                    break;
                }
                CraftItemEvent craftItemEvent;
                this.server.getPluginManager().callEvent(craftItemEvent = new CraftItemEvent(this, ingredients, recipe));

                if (craftItemEvent.isCancelled()) {
                    this.inventory.sendContents(this);
                    break;
                }

                for (int i = 0; i < used.length; i++) {
                    int count = used[i];
                    if (count == 0) {
                        continue;
                    }

                    item = this.inventory.getItem(i);

                    Item newItem;
                    if (item.getCount() > count) {
                        newItem = item.clone();
                        newItem.setCount(item.getCount() - count);
                    } else {
                        newItem = new ItemBlock(new BlockAir(), 0, 0);
                    }

                    this.inventory.setItem(i, newItem);
                }

                Item[] extraItem = this.inventory.addItem(recipe.getResult());
                if (extraItem.length > 0) {
                    for (Item i : extraItem) {
                        this.level.dropItem(this, i);
                    }
                }
                //todo: achievement

                break;
            case ProtocolInfo.CONTAINER_SET_SLOT_PACKET:
                if (!this.spawned || this.blocked || !this.isAlive()) {
                    break;
                }

                ContainerSetSlotPacket containerSetSlotPacket = (ContainerSetSlotPacket) packet;
                if (containerSetSlotPacket.slot < 0) {
                    break;
                }

                BaseTransaction transaction;
                if (containerSetSlotPacket.windowid == 0) { //Our inventory
                    if (containerSetSlotPacket.slot >= this.inventory.getSize()) {
                        break;
                    }
                    if (this.isCreative()) {
                        if (Item.getCreativeItemIndex(containerSetSlotPacket.item) != -1) {
                            this.inventory.setItem(containerSetSlotPacket.slot, containerSetSlotPacket.item);
                            this.inventory.setHotbarSlotIndex(containerSetSlotPacket.slot, containerSetSlotPacket.slot); //links hotbar[packet.slot] to slots[packet.slot]
                        }
                    }
                    transaction = new BaseTransaction(this.inventory, containerSetSlotPacket.slot, this.inventory.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                } else if (containerSetSlotPacket.windowid == ContainerSetContentPacket.SPECIAL_ARMOR) { //Our armor
                    if (containerSetSlotPacket.slot >= 4) {
                        break;
                    }

                    transaction = new BaseTransaction(this.inventory, containerSetSlotPacket.slot + this.inventory.getSize(), this.inventory.getArmorItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                } else if (this.windowIndex.containsKey(containerSetSlotPacket.windowid)) {
                    this.craftingType = 0;
                    Inventory inv = this.windowIndex.get(containerSetSlotPacket.windowid);

                    if (inv instanceof EnchantInventory && containerSetSlotPacket.item.hasEnchantments()) {
                        ((EnchantInventory) inv).onEnchant(this, inv.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                    }

                    transaction = new BaseTransaction(inv, containerSetSlotPacket.slot, inv.getItem(containerSetSlotPacket.slot), containerSetSlotPacket.item);
                } else {
                    break;
                }

                if (transaction.getSourceItem().deepEquals(transaction.getTargetItem()) && transaction.getTargetItem().getCount() == transaction.getSourceItem().getCount()) { //No changes!
                    //No changes, just a local inventory update sent by the server
                    break;
                }


                if (this.currentTransaction == null || this.currentTransaction.getCreationTime() < (System.currentTimeMillis() - 8 * 1000)) {
                    if (this.currentTransaction != null) {
                        for (Inventory inventory : this.currentTransaction.getInventories()) {
                            if (inventory instanceof PlayerInventory) {
                                ((PlayerInventory) inventory).sendArmorContents(this);
                            }
                            inventory.sendContents(this);
                        }
                    }
                    this.currentTransaction = new SimpleTransactionGroup(this);
                }

                this.currentTransaction.addTransaction(transaction);

                if (this.currentTransaction.canExecute()) {
                    //todo achievement

                    this.currentTransaction.execute();

                    this.currentTransaction = null;
                }

                break;
            case ProtocolInfo.BLOCK_ENTITY_DATA_PACKET:
                if (!this.spawned || this.blocked || !this.isAlive()) {
                    break;
                }
                BlockEntityDataPacket blockEntityDataPacket = (BlockEntityDataPacket) packet;
                this.craftingType = 0;

                pos = new Vector3(blockEntityDataPacket.x, blockEntityDataPacket.y, blockEntityDataPacket.z);
                if (pos.distanceSquared(this) > 10) {
                    break;
                }

                BlockEntity t = this.level.getBlockEntity(pos);
                if (t instanceof BlockEntitySign) {
                    CompoundTag nbt;
                    try {
                        nbt = NBTIO.read(blockEntityDataPacket.namedTag, ByteOrder.LITTLE_ENDIAN);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (!BlockEntity.SIGN.equals(nbt.getString("id"))) {
                        ((BlockEntitySign) t).spawnTo(this);
                    } else {
                        SignChangeEvent signChangeEvent = new SignChangeEvent(t.getBlock(), this, new String[]{
                                TextFormat.clean(nbt.getString("Text1"), this.removeFormat),
                                TextFormat.clean(nbt.getString("Text2"), this.removeFormat),
                                TextFormat.clean(nbt.getString("Text3"), this.removeFormat),
                                TextFormat.clean(nbt.getString("Text4"), this.removeFormat)
                        });

                        if (!t.namedTag.contains("Creator") || !Objects.equals(this.getUniqueId().toString(), t.namedTag.getString("Creator"))) {
                            signChangeEvent.setCancelled();
                        } else {
                            for (String line : signChangeEvent.getLines()) {
                                if (line.length() > 16) {
                                    signChangeEvent.setCancelled();
                                }
                            }
                        }

                        this.server.getPluginManager().callEvent(signChangeEvent);

                        if (!signChangeEvent.isCancelled()) {
                            ((BlockEntitySign) t).setText(signChangeEvent.getLine(0), signChangeEvent.getLine(1), signChangeEvent.getLine(2), signChangeEvent.getLine(3));
                        } else {
                            ((BlockEntitySign) t).spawnTo(this);
                        }

                    }
                }
                break;
            case ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET:
                RequestChunkRadiusPacket requestChunkRadiusPacket = (RequestChunkRadiusPacket) packet;
                ChunkRadiusUpdatePacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatePacket();
                chunkRadiusUpdatePacket.radius = this.viewDistance;
                this.dataPacket(chunkRadiusUpdatePacket);
                break;
            default:
                break;
        }

    }

    public boolean kick() {
        return this.kick("");
    }

    public boolean kick(String reason) {
        return this.kick(reason, true);
    }

    public boolean kick(String reason, boolean isAdmin) {
        PlayerKickEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerKickEvent(this, reason, this.getLeaveMessage()));
        if (!ev.isCancelled()) {
            String message;
            if (isAdmin) {
                if (!this.isBanned()) {
                    message = "Kicked by admin." + (!"".equals(reason) ? " Reason: " + reason : "");
                } else {
                    message = reason;
                }
            } else {
                if ("".equals(reason)) {
                    message = "disconnectionScreen.noReason";
                } else {
                    message = reason;
                }
            }

            this.close(ev.getQuitMessage(), message);

            return true;
        }

        return false;
    }

    @Override
    public void sendMessage(String message) {
        String[] mes = this.server.getLanguage().translateString(message).split("\\n");
        for (String m : mes) {
            if (!"".equals(m)) {
                TextPacket pk = new TextPacket();
                pk.type = TextPacket.TYPE_RAW;
                pk.message = m;
                this.dataPacket(pk);
            }
        }
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer) {
            this.sendTranslation(message.getText(), ((TranslationContainer) message).getParameters());
            return;
        }
        this.sendMessage(message.getText());
    }

    public void sendTranslation(String message, String... parameters) {
        TextPacket pk = new TextPacket();
        if (!this.server.isLanguageForced()) {
            pk.type = TextPacket.TYPE_TRANSLATION;
            pk.message = this.server.getLanguage().translateString(message, parameters, "nukkit.");
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = this.server.getLanguage().translateString(parameters[i], parameters, "nukkit.");

            }
            pk.parameters = parameters;
        } else {
            pk.type = TextPacket.TYPE_RAW;
            pk.message = this.server.getLanguage().translateString(message, parameters);
        }
        this.dataPacket(pk);
    }

    public void sendPopup(String message) {
        this.sendPopup(message, "");
    }

    public void sendPopup(String message, String subtitle) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_POPUP;
        pk.source = message;
        pk.message = subtitle;
        this.dataPacket(pk);
    }

    @Deprecated
    public void sendTip(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_TIP;
        pk.message = message;
        this.dataPacket(pk);
    }

    @Override
    public void close() {
        this.close("");
    }

    public void close(String message) {
        this.close(message, "generic");
    }

    public void close(String message, String reason) {
        this.close(message, reason, true);
    }

    public void close(String message, String reason, boolean notify) {
        this.close(new TextContainer(message), reason, notify);
    }

    public void close(TextContainer message) {
        this.close(message, "generic");
    }

    public void close(TextContainer message, String reason) {
        this.close(message, reason, true);
    }

    public void close(TextContainer message, String reason, boolean notify) {
        if (this.connected && !this.closed) {
            if (notify && reason.length() > 0) {
                DisconnectPacket pk = new DisconnectPacket();
                pk.message = reason;
                this.directDataPacket(pk);
            }

            this.connected = false;
            PlayerQuitEvent ev = null;
            if (this.getName() != null && this.getName().length() > 0) {
                this.server.getPluginManager().callEvent(ev = new PlayerQuitEvent(this, message, true));
                if (this.loggedIn && ev.getAutoSave()) {
                    this.save();
                }
            }

            for (Player player : new ArrayList<>(this.server.getOnlinePlayers().values())) {
                if (!player.canSee(this)) {
                    player.showPlayer(this);
                }
            }

            this.hiddenPlayers = new HashMap<>();

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                this.removeWindow(window);
            }

            for (String index : new ArrayList<>(this.usedChunks.keySet())) {
                Chunk.Entry entry = Level.getChunkXZ(index);
                this.level.unregisterChunkLoader(this, entry.chunkX, entry.chunkZ);
                this.usedChunks.remove(index);
            }

            super.close();

            this.interfaz.close(this, notify ? reason : "");

            if (this.loggedIn) {
                this.server.removeOnlinePlayer(this);
            }

            this.loggedIn = false;

            if (ev != null && !Objects.equals(this.username, "") && this.spawned && !Objects.equals(ev.getQuitMessage().toString(), "")) {
                this.server.broadcastMessage(ev.getQuitMessage());
            }

            this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
            this.spawned = false;
            this.server.logger.info(this.getServer().getLanguage().translateString("nukkit.player.logOut", new String[]{
                    TextFormat.AQUA + (this.getName() == null ? "" : this.getName()) + TextFormat.WHITE,
                    this.ip,
                    String.valueOf(this.port),
                    this.getServer().getLanguage().translateString(reason)
            }));
            this.windows = new HashMap<>();
            this.windowIndex = new HashMap<>();
            this.usedChunks = new HashMap<>();
            this.loadQueue = new HashMap<>();
            this.hasSpawned = new HashMap<>();
            this.spawnPosition = null;

            if (this.riding instanceof EntityVehicle) {
                ((EntityVehicle) this.riding).linkedEntity = null;
            }

            this.riding = null;
        }

        if (this.perm != null) {
            this.perm.clearPermissions();
            this.perm = null;
        }

        if (this.inventory != null) {
            this.inventory = null;
            this.currentTransaction = null;
        }

        this.chunk = null;

        this.server.removePlayer(this);
    }

    public void save() {
        this.save(false);
    }

    public void save(boolean async) {
        if (this.closed) {
            throw new IllegalStateException("Tried to save closed player");
        }

        super.saveNBT();

        if (this.level != null) {
            this.namedTag.putString("Level", this.level.getFolderName());
            if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
                this.namedTag.putString("SpawnLevel", this.spawnPosition.getLevel().getFolderName());
                this.namedTag.putInt("SpawnX", (int) this.spawnPosition.x);
                this.namedTag.putInt("SpawnY", (int) this.spawnPosition.y);
                this.namedTag.putInt("SpawnZ", (int) this.spawnPosition.z);
            }

            //todo save achievement

            this.namedTag.putInt("playerGameType", this.gamemode);
            this.namedTag.putLong("lastPlayed", System.currentTimeMillis() / 1000);

            this.namedTag.putString("lastIP", this.getAddress());

            this.namedTag.putInt("EXP", this.getExperience());
            this.namedTag.putInt("expLevel", this.getExperienceLevel());

            this.namedTag.putInt("foodLevel", this.getFoodData().getLevel());
            this.namedTag.putFloat("foodSaturationLevel", this.getFoodData().getFoodSaturationLevel());

            if (!"".equals(this.username) && this.namedTag != null) {
                this.server.saveOfflinePlayerData(this.username, this.namedTag, async);
            }
        }
    }

    public String getName() {
        return this.username;
    }

    @Override
    public void kill() {
        if (!this.spawned) {
            return;
        }

        String message = "death.attack.generic";

        List<String> params = new ArrayList<>();
        params.add(this.getDisplayName());

        EntityDamageEvent cause = this.getLastDamageCause();

        switch (cause == null ? EntityDamageEvent.CAUSE_CUSTOM : cause.getCause()) {
            case EntityDamageEvent.CAUSE_ENTITY_ATTACK:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.player";
                        params.add(((Player) e).getDisplayName());
                        break;
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.mob";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                        break;
                    } else {
                        params.add("Unknown");
                    }
                }
                break;
            case EntityDamageEvent.CAUSE_PROJECTILE:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.arrow";
                        params.add(((Player) e).getDisplayName());
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.arrow";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                        break;
                    } else {
                        params.add("Unknown");
                    }
                }
                break;
            case EntityDamageEvent.CAUSE_SUICIDE:
                message = "death.attack.generic";
                break;
            case EntityDamageEvent.CAUSE_VOID:
                message = "death.attack.outOfWorld";
                break;
            case EntityDamageEvent.CAUSE_FALL:
                if (cause != null) {
                    if (cause.getFinalDamage() > 2) {
                        message = "death.fell.accident.generic";
                        break;
                    }
                }
                message = "death.attack.fall";
                break;

            case EntityDamageEvent.CAUSE_SUFFOCATION:
                message = "death.attack.inWall";
                break;

            case EntityDamageEvent.CAUSE_LAVA:
                message = "death.attack.lava";
                break;

            case EntityDamageEvent.CAUSE_FIRE:
                message = "death.attack.onFire";
                break;

            case EntityDamageEvent.CAUSE_FIRE_TICK:
                message = "death.attack.inFire";
                break;

            case EntityDamageEvent.CAUSE_DROWNING:
                message = "death.attack.drown";
                break;

            case EntityDamageEvent.CAUSE_CONTACT:
                if (cause instanceof EntityDamageByBlockEvent) {
                    if (((EntityDamageByBlockEvent) cause).getDamager().getId() == Block.CACTUS) {
                        message = "death.attack.cactus";
                    }
                }
                break;

            case EntityDamageEvent.CAUSE_BLOCK_EXPLOSION:
            case EntityDamageEvent.CAUSE_ENTITY_EXPLOSION:
                if (cause instanceof EntityDamageByEntityEvent) {
                    Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                    killer = e;
                    if (e instanceof Player) {
                        message = "death.attack.explosion.player";
                        params.add(((Player) e).getDisplayName());
                    } else if (e instanceof EntityLiving) {
                        message = "death.attack.explosion.player";
                        params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : ((EntityLiving) e).getName());
                        break;
                    }
                } else {
                    message = "death.attack.explosion";
                }
                break;

            case EntityDamageEvent.CAUSE_MAGIC:
                message = "death.attack.magic";
                break;

            case EntityDamageEvent.CAUSE_CUSTOM:
                break;

            default:

        }

        this.health = 0;
        this.scheduleUpdate();

        PlayerDeathEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerDeathEvent(this, this.getDrops(), new TranslationContainer(message, params.stream().toArray(String[]::new)), this.getExperienceLevel()));

        if (!ev.getKeepInventory()) {
            for (Item item : ev.getDrops()) {
                this.level.dropItem(this, item);
            }

            if (this.inventory != null) {
                this.inventory.clearAll();
            }
        }

        if (!ev.getKeepExperience()) {
            if (this.isSurvival() || this.isAdventure()) {
                int exp = ev.getExperience() * 7;
                if (exp > 100) exp = 100;
                int add = 1;
                for (int ii = 1; ii < exp; ii += add) {
                    this.getLevel().dropExpOrb(this, add);
                    add = new NukkitRandom().nextRange(1, 3);
                }
            }
            this.setExperience(0, 0);
        }

        if (!Objects.equals(ev.getDeathMessage().toString(), "")) {
            this.server.broadcast(ev.getDeathMessage(), Server.BROADCAST_CHANNEL_USERS);
        }


        RespawnPacket pk = new RespawnPacket();
        Position pos = this.getSpawn();
        pk.x = (float) pos.x;
        pk.y = (float) pos.y;
        pk.z = (float) pos.z;
        this.dataPacket(pk);
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(health);
        Attribute attr = Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0);
        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{attr};
            pk.entityId = 0;
            this.dataPacket(pk);
        }
    }

    public int getExperience() {
        return this.exp;
    }

    public int getExperienceLevel() {
        return this.expLevel;
    }

    public void addExperience(int add) {
        if (add == 0) return;
        int now = this.getExperience();
        int added = now + add;
        int level = this.getExperienceLevel();
        int most = calculateRequireExperience(level);
        while (added >= most) {  //Level Up!
            added = added - most;
            level++;
            most = calculateRequireExperience(level);
        }
        this.setExperience(added, level);
    }

    public static int calculateRequireExperience(int level) {
        if (level < 16) {
            return 2 * level + 7;
        } else if (level >= 17 && level <= 31) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    public void setExperience(int exp) {
        setExperience(exp, this.getExperienceLevel());
    }

    //todo something on performance, lots of exp orbs then lots of packets, could crash client

    public void setExperience(int exp, int level) {
        this.exp = exp;
        this.expLevel = level;

        this.sendExperienceLevel(level);
        this.sendExperience(exp);
    }

    public void sendExperience() {
        sendExperience(this.getExperience());
    }

    public void sendExperience(int exp) {
        float percent = ((float) exp) / calculateRequireExperience(this.getExperienceLevel());
        this.setAttribute(Attribute.addAttribute(Attribute.EXPERIENCE, "player.experience", 0, 1, percent, true).setValue(percent));
    }

    public void sendExperienceLevel() {
        sendExperienceLevel(this.getExperienceLevel());
    }

    public void sendExperienceLevel(int level) {
        this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(level));
    }

    public void setAttribute(Attribute attribute) {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entries = new Attribute[]{attribute};
        pk.entityId = 0;
        this.dataPacket(pk);
    }

    @Override
    public void setMovementSpeed(float speed) {
        super.setMovementSpeed(speed);
        if (this.spawned) {
            Attribute attribute = Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(speed);
            this.setAttribute(attribute);
        }
    }

    public Entity getKiller() {
        return killer;
    }

    @Override
    public void attack(EntityDamageEvent source) {
        if (!this.isAlive()) {
            return;
        }

        if (this.isCreative()
                && source.getCause() != EntityDamageEvent.CAUSE_MAGIC
                && source.getCause() != EntityDamageEvent.CAUSE_SUICIDE
                && source.getCause() != EntityDamageEvent.CAUSE_VOID
                ) {
            source.setCancelled();
        } else if (this.allowFlight && source.getCause() == EntityDamageEvent.CAUSE_FALL) {
            source.setCancelled();
        } else if (source.getCause() == EntityDamageEvent.CAUSE_FALL) {
            if (this.getLevel().getBlock(this.getPosition().floor().add(0.5, -1, 0.5)).getId() == Block.SLIME_BLOCK) {
                if (!this.isSneaking()) {
                    source.setCancelled();
                    this.resetFallDistance();
                }
            }
        }

        if (source instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
            if (damager instanceof Player) {
                ((Player) damager).getFoodData().updateFoodExpLevel(0.3);
            }
            //暴击
            boolean add = false;
            if (!damager.onGround) {
                NukkitRandom random = new NukkitRandom();
                for (int i = 0; i < 5; i++) {
                    CriticalParticle par = new CriticalParticle(new Vector3(this.x + random.nextRange(-15, 15) / 10, this.y + random.nextRange(0, 20) / 10, this.z + random.nextRange(-15, 15) / 10));
                    this.getLevel().addParticle(par);
                }

                add = true;
            }
            if (add) source.setDamage((float) (source.getDamage() * 1.5));
        }

        super.attack(source);

        if (!source.isCancelled() && this.getLastDamageCause() == source && this.spawned) {
            this.getFoodData().updateFoodExpLevel(0.3);
            EntityEventPacket pk = new EntityEventPacket();
            pk.eid = 0;
            pk.event = EntityEventPacket.HURT_ANIMATION;
            this.dataPacket(pk);
        }
    }

    public void sendPosition(Vector3 pos) {
        this.sendPosition(pos, this.yaw);
    }

    public void sendPosition(Vector3 pos, double yaw) {
        this.sendPosition(pos, yaw, this.pitch);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch) {
        this.sendPosition(pos, yaw, pitch, MovePlayerPacket.MODE_NORMAL);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, byte mode) {
        this.sendPosition(pos, yaw, pitch, mode, null);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, byte mode, Player[] targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) pos.x;
        pk.y = (float) (pos.y + this.getEyeHeight());
        pk.z = (float) pos.z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;

        if (targets != null) {
            Server.broadcastPacket(targets, pk);
        } else {
            pk.eid = 0;
            this.dataPacket(pk);
        }
    }

    @Override
    protected void checkChunks() {
        if (this.chunk == null || (this.chunk.getX() != ((int) this.x >> 4) || this.chunk.getZ() != ((int) this.z >> 4))) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true);

            if (!this.justCreated) {
                Map<Integer, Player> newChunk = this.level.getChunkPlayers((int) this.x >> 4, (int) this.z >> 4);
                newChunk.remove(this.getLoaderId());

                //List<Player> reload = new ArrayList<>();
                for (Player player : new ArrayList<>(this.hasSpawned.values())) {
                    if (!newChunk.containsKey(player.getLoaderId())) {
                        this.despawnFrom(player);
                    } else {
                        newChunk.remove(player.getLoaderId());
                        //reload.add(player);
                    }
                }

                for (Player player : newChunk.values()) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    protected boolean checkTeleportPosition() {
        if (this.teleportPosition != null) {
            int chunkX = (int) this.teleportPosition.x >> 4;
            int chunkZ = (int) this.teleportPosition.z >> 4;

            for (int X = -1; X <= 1; ++X) {
                for (int Z = -1; Z <= 1; ++Z) {
                    String index = Level.chunkHash(chunkX + X, chunkZ + Z);
                    if (!this.usedChunks.containsKey(index) || !this.usedChunks.get(index)) {
                        return false;
                    }
                }
            }

            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);
            this.spawnToAll();
            this.forceMovement = this.teleportPosition;
            this.teleportPosition = null;

            return true;
        }

        return false;
    }

    @Override
    public boolean teleport(Location location, TeleportCause cause) {
        if (!this.isOnline()) {
            return false;
        }

        Location from = this.getLocation();
        Location to = location;

        if (cause != null) {
            PlayerTeleportEvent event = new PlayerTeleportEvent(this, from, to, cause);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            to = event.getTo();
        }

        Position oldPos = this.getPosition();
        if (super.teleport(to, null)) { // null to prevent fire of duplicate EntityTeleportEvent

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                if (window == this.inventory) {
                    continue;
                }
                this.removeWindow(window);
            }

            this.teleportPosition = new Vector3(this.x, this.y, this.z);

            if (!this.checkTeleportPosition()) {
                this.forceMovement = oldPos;
            } else {
                this.spawnToAll();
            }

            this.resetFallDistance();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            //Weather
            this.getLevel().sendWeather(this);
            //Update time
            this.getLevel().sendTime(this);
            return true;
        }

        return false;
    }

    public void teleportImmediate(Location location) {
        this.teleportImmediate(location, TeleportCause.PLUGIN);
    }

    public void teleportImmediate(Location location, TeleportCause cause) {
        if (super.teleport(location, cause)) {

            for (Inventory window : new ArrayList<>(this.windowIndex.values())) {
                if (window == this.inventory) {
                    continue;
                }
                this.removeWindow(window);
            }

            this.forceMovement = new Vector3(this.x, this.y, this.z);
            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);

            this.resetFallDistance();
            this.orderChunks();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            //Weather
            this.getLevel().sendWeather(this);
            //Update time
            this.getLevel().sendTime(this);
        }
    }

    public int getWindowId(Inventory inventory) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }

        return -1;
    }

    public int addWindow(Inventory inventory) {
        return this.addWindow(inventory, null);
    }

    public int addWindow(Inventory inventory, Integer forceId) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }
        int cnt;
        if (forceId == null) {
            this.windowCnt = cnt = Math.max(2, ++this.windowCnt % 99);
        } else {
            cnt = forceId;
        }
        this.windowIndex.put(cnt, inventory);
        this.windows.put(inventory, cnt);
        if (inventory.open(this)) {
            return cnt;
        } else {
            this.removeWindow(inventory);

            return -1;
        }
    }

    public void removeWindow(Inventory inventory) {
        inventory.close(this);
        if (this.windows.containsKey(inventory)) {
            int id = this.windows.get(inventory);
            this.windows.remove(this.windowIndex.get(id));
            this.windowIndex.remove(id);
        }
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        this.server.getPlayerMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        this.server.getPlayerMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @Override
    public void onChunkChanged(FullChunk chunk) {
        this.loadQueue.put(Level.chunkHash(chunk.getX(), chunk.getZ()), Math.abs(((int) this.x >> 4) - chunk.getX()) + Math.abs(((int) this.z >> 4) - chunk.getZ()));
    }

    @Override
    public void onChunkLoaded(FullChunk chunk) {

    }

    @Override
    public void onChunkPopulated(FullChunk chunk) {

    }

    @Override
    public void onChunkUnloaded(FullChunk chunk) {

    }

    @Override
    public void onBlockChanged(Vector3 block) {

    }

    @Override
    public Integer getLoaderId() {
        return this.loaderId;
    }

    @Override
    public boolean isLoaderActive() {
        return this.isConnected();
    }


    public static BatchPacket getChunkCacheFromData(int chunkX, int chunkZ, byte[] payload) {
        return getChunkCacheFromData(chunkX, chunkZ, payload, FullChunkDataPacket.ORDER_COLUMNS);
    }

    public static BatchPacket getChunkCacheFromData(int chunkX, int chunkZ, byte[] payload, byte ordering) {
        FullChunkDataPacket pk = new FullChunkDataPacket();
        pk.chunkX = chunkX;
        pk.chunkZ = chunkZ;
        pk.order = ordering;
        pk.data = payload;
        pk.encode();

        BatchPacket batch = new BatchPacket();
        byte[][] batchPayload = new byte[2][];
        byte[] buf = pk.getBuffer();
        batchPayload[0] = Binary.writeInt(buf.length);
        batchPayload[1] = buf;
        byte[] data = Binary.appendBytes(batchPayload);
        try {
            batch.payload = Zlib.deflate(data, ServerProperties.network_compression_level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        batch.encode();
        batch.isEncoded = true;
        return batch;
    }

    private boolean foodEnabled = true;

    public boolean isFoodEnabled() {
        return !(this.isCreative() || this.isSpectator()) && this.foodEnabled;
    }

    public void setFoodEnabled(boolean foodEnabled) {
        this.foodEnabled = foodEnabled;
    }

    public PlayerFood getFoodData() {
        return this.foodData;
    }

    public synchronized void setLocale(Locale locale) {
        this.locale.set(locale);
    }

    public synchronized Locale getLocale() {
        return this.locale.get();
    }

    @Override
    public int hashCode() {
        if ((this.hash == 0) || (this.hash == 485)) {
            this.hash = (485 + (getUniqueId() != null ? getUniqueId().hashCode() : 0));
        }
        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Player)) {
            return false;
        }
        Player other = (Player) obj;
        return Objects.equals(this.getUniqueId(), other.getUniqueId()) && this.getId() == other.getId();
    }
}
