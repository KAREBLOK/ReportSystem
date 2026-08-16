package com.reportsystem.spigot.recording;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.utils.SkinUtils;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplayPacketListener extends PacketListenerAbstract {

    private final RecordingSession session;
    private final Player recordedPlayer;
    private final JavaPlugin plugin;
    private long lastPositionTime = 0;
    private Location lastLocation;
    private Vector lastVelocity = new Vector(0, 0, 0);
    // volatile: paket işleme thread'i (netty) ile diğer erişimler arası görünürlük garantisi
    private volatile boolean isUsingItem = false;
    private volatile long itemUseStartTime = 0;
    private boolean wasOnFire = false; // YENİ: Yanma durumu takibi
    private int packetCount = 0; // Debug counter

    public ReplayPacketListener(RecordingSession session, Player recordedPlayer, JavaPlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.session = session;
        this.recordedPlayer = recordedPlayer;
        this.plugin = plugin;
        this.lastLocation = recordedPlayer.getLocation();

        // İlk skin bilgisini kaydet
        String[] skinData = SkinUtils.getSkinData(recordedPlayer);
        session.addAction(new PlayerInfoAction(skinData[0], skinData[1], recordedPlayer.getName()));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        // user.getUUID() handshake/status fazinda null donebilir (server list ping)
        if (user == null || user.getUUID() == null
                || !user.getUUID().equals(recordedPlayer.getUniqueId())) {
            return;
        }

        // Oyuncu geçerli değilse işlem yapma
        if (!recordedPlayer.isOnline() || !recordedPlayer.isValid()) {
            return;
        }

        PacketTypeCommon packetType = event.getPacketType();

        // Debug: Her 100 pakette bir log
        packetCount++;
        if (packetCount % 100 == 0) {
            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] " + recordedPlayer.getName() + " - " + packetCount + " paket alındı, session size: " + session.getActions().size());
        }

        // Oyuncu hareket paketleri
        if (packetType.equals(PacketType.Play.Client.PLAYER_POSITION) ||
                packetType.equals(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) ||
                packetType.equals(PacketType.Play.Client.PLAYER_ROTATION) ||
                packetType.equals(PacketType.Play.Client.PLAYER_FLYING)) {

            handleMovementPacket(event, packetType);

            // Animasyon paketi
        } else if (packetType.equals(PacketType.Play.Client.ANIMATION)) {
            WrapperPlayClientAnimation wrapper = new WrapperPlayClientAnimation(event);

            AnimationAction.AnimationType animType = AnimationAction.AnimationType.SWING_MAIN_HAND;
            if (wrapper.getHand() == InteractionHand.OFF_HAND) {
                animType = AnimationAction.AnimationType.SWING_OFF_HAND;
            }

            session.addAction(new AnimationAction(animType));

            // Entity durumu değişiklikleri (eğilme, koşma vb.)
        } else if (packetType.equals(PacketType.Play.Client.ENTITY_ACTION)) {
            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);

            switch (wrapper.getAction()) {
                case START_SNEAKING:
                    session.addAction(new EntityStateAction(EntityStateAction.StateType.SNEAKING, true));
                    session.addAction(new PoseAction(PoseAction.PoseType.SNEAKING));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sneaking started");
                    break;
                case STOP_SNEAKING:
                    session.addAction(new EntityStateAction(EntityStateAction.StateType.SNEAKING, false));
                    session.addAction(new PoseAction(PoseAction.PoseType.STANDING));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sneaking stopped");
                    break;
                case START_SPRINTING:
                    session.addAction(new EntityStateAction(EntityStateAction.StateType.SPRINTING, true));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sprinting started");
                    break;
                case STOP_SPRINTING:
                    session.addAction(new EntityStateAction(EntityStateAction.StateType.SPRINTING, false));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sprinting stopped");
                    break;
                case START_FLYING_WITH_ELYTRA:
                    session.addAction(new EntityStateAction(EntityStateAction.StateType.GLIDING, true));
                    session.addAction(new PoseAction(PoseAction.PoseType.FALL_FLYING));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Elytra flying started");
                    break;
            }

            // USE ITEM paketi - Yemek yeme, kalkan kullanma vb.
        } else if (packetType.equals(PacketType.Play.Client.USE_ITEM)) {
            WrapperPlayClientUseItem wrapper = new WrapperPlayClientUseItem(event);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!recordedPlayer.isOnline()) return;
                ItemStack item = wrapper.getHand() == InteractionHand.MAIN_HAND ?
                        recordedPlayer.getInventory().getItemInMainHand() :
                        recordedPlayer.getInventory().getItemInOffHand();

                if (item != null && item.getType() != Material.AIR) {
                    boolean isMainHand = wrapper.getHand() == InteractionHand.MAIN_HAND;

                    // Kalkan
                    if (item.getType() == Material.SHIELD) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.SHIELD_BLOCK, 0, isMainHand, true));
                        isUsingItem = true;
                        itemUseStartTime = System.currentTimeMillis();
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Shield use started");
                    }
                    // Yay
                    else if (item.getType() == Material.BOW) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.BOW_CHARGE, 0, isMainHand, true));
                        isUsingItem = true;
                        itemUseStartTime = System.currentTimeMillis();
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Bow charge started");
                    }
                    // Arbalet
                    else if (item.getType() == Material.CROSSBOW) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.CROSSBOW_CHARGE, 0, isMainHand, true));
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Crossbow charge started");
                    }
                    // Trident
                    else if (item.getType() == Material.TRIDENT) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.TRIDENT_CHARGE, 0, isMainHand, true));
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Trident charge started");
                    }
                    // Yemek
                    else if (item.getType().isEdible()) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.FOOD_EAT, 0, isMainHand, true));
                        isUsingItem = true;
                        itemUseStartTime = System.currentTimeMillis();
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Food eating started: " + item.getType());
                    }
                    // İksir
                    else if (item.getType().name().contains("POTION") && !item.getType().name().contains("SPLASH")) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.POTION_DRINK, 0, isMainHand, true));
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Potion drinking started");
                    }
                    // Süt
                    else if (item.getType() == Material.MILK_BUCKET) {
                        session.addAction(new UseItemAction(UseItemAction.UseType.MILK_DRINK, 0, isMainHand, true));
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Milk drinking started");
                    }
                }
            });

            // HELD_ITEM_CHANGE paketi - Eldeki item değişimi
        } else if (packetType.equals(PacketType.Play.Client.HELD_ITEM_CHANGE)) {
            WrapperPlayClientHeldItemChange wrapper = new WrapperPlayClientHeldItemChange(event);
            int newSlot = wrapper.getSlot();

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Held item change to slot: " + newSlot);

            // Bir tick bekle ve yeni item'ı kaydet
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!recordedPlayer.isOnline()) return;
                ItemStack mainHandItem = recordedPlayer.getInventory().getItemInMainHand();
                recordEquipmentItem(mainHandItem, EquipmentAction.EquipmentSlot.MAIN_HAND);
            }, 1L);

            // Blok yerleştirme
        } else if (packetType.equals(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT)) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i blockPos = wrapper.getBlockPosition();

            // Kaydet
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!recordedPlayer.isOnline()) return;
                org.bukkit.block.Block block = recordedPlayer.getWorld().getBlockAt(
                        blockPos.getX(), blockPos.getY(), blockPos.getZ()
                );

                session.addAction(new BlockAction(BlockAction.BlockActionType.PLACE_BLOCK,
                        blockPos.getX(), blockPos.getY(), blockPos.getZ(), block.getType().name()));
            });

            // Blok kırma
        } else if (packetType.equals(PacketType.Play.Client.PLAYER_DIGGING)) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            Vector3i blockPos = wrapper.getBlockPosition();

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Digging action: " + wrapper.getAction() +
                    " at " + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ());

            if (wrapper.getAction() == DiggingAction.START_DIGGING) {
                // Blok tipini kaydet (replay'de göstermek için - blok artık gerçek dünyada olmayabilir).
                // KRİTİK: Bu handler netty (async) thread'inde çalışır; getWorld()/getBlockAt()
                // Bukkit çağrıları MUTLAKA ana thread'de yapılmalı (async block access hatası + yanlış okuma).
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!recordedPlayer.isOnline()) return;
                    String blockTypeName = recordedPlayer.getWorld()
                            .getBlockAt(blockPos.getX(), blockPos.getY(), blockPos.getZ())
                            .getType().name();
                    session.addAction(new BlockAction(BlockAction.BlockActionType.START_BREAKING,
                            blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0, blockTypeName));
                });
            } else if (wrapper.getAction() == DiggingAction.FINISHED_DIGGING) {
                // Blok kırma tamamlandı
                session.addAction(new BlockAction(BlockAction.BlockActionType.STOP_BREAKING,
                        blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0));
            } else if (wrapper.getAction() == DiggingAction.CANCELLED_DIGGING) {
                // İptal edildi
                session.addAction(new BlockAction(BlockAction.BlockActionType.STOP_BREAKING,
                        blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0));
            } else if (wrapper.getAction() == DiggingAction.RELEASE_USE_ITEM) {
                // Item kullanımı bitti (yay, kalkan vb.)
                if (isUsingItem) {
                    isUsingItem = false;
                    // Parantez düzeltmesi: önce fark alınıp sonra 50'ye bölünür (tick'e çevir)
                    final int duration = (int) ((System.currentTimeMillis() - itemUseStartTime) / 50);

                    // KRİTİK: getInventory() Bukkit çağrısı ana thread'de yapılmalı (netty thread değil).
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!recordedPlayer.isOnline()) return;
                        ItemStack item = recordedPlayer.getInventory().getItemInMainHand();
                        if (item != null && item.getType() == Material.SHIELD) {
                            session.addAction(new UseItemAction(UseItemAction.UseType.SHIELD_BLOCK, duration, true, false));
                            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Shield use ended");
                        } else if (item != null && item.getType() == Material.BOW) {
                            session.addAction(new UseItemAction(UseItemAction.UseType.BOW_CHARGE, duration, true, false));
                            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Bow charge released");
                        }
                    });
                }
            }
        }

        // Debug için
        ReportSystemSpigot.getInstance().debug("[PACKET-DEBUG] Received: " + packetType.getName());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        // user.getUUID() handshake/status fazinda null donebilir
        if (user == null || user.getUUID() == null
                || !user.getUUID().equals(recordedPlayer.getUniqueId())) {
            return;
        }

        // Oyuncu geçerli değilse işlem yapma
        if (!recordedPlayer.isOnline() || !recordedPlayer.isValid()) {
            return;
        }

        // Entity Velocity paketi - oyuncunun hızını yakala
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            if (wrapper.getEntityId() == recordedPlayer.getEntityId()) {
                Vector3d velocity = wrapper.getVelocity();

                // Velocity değişimi önemliyse kaydet
                Vector newVelocity = new Vector(velocity.getX(), velocity.getY(), velocity.getZ());
                if (newVelocity.distance(lastVelocity) > 0.1) {
                    session.addAction(new VelocityAction(velocity.getX(), velocity.getY(), velocity.getZ()));
                    lastVelocity = newVelocity;

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Velocity recorded: " +
                            String.format("%.2f, %.2f, %.2f", velocity.getX(), velocity.getY(), velocity.getZ()));
                }
            }
        }

        // Blok kırma animasyonunu yakala
        else if (event.getPacketType() == PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
            WrapperPlayServerBlockBreakAnimation wrapper = new WrapperPlayServerBlockBreakAnimation(event);

            Vector3i pos = wrapper.getBlockPosition();
            session.addAction(new BlockAction(BlockAction.BlockActionType.BREAK_PROGRESS,
                    pos.getX(), pos.getY(), pos.getZ(), wrapper.getDestroyStage()));

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Block break animation recorded: stage " +
                    wrapper.getDestroyStage() + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        // Entity Metadata - Pose değişiklikleri
        else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            if (wrapper.getEntityId() == recordedPlayer.getEntityId()) {
                // Metadata'dan pose bilgisini çıkar
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Player metadata update");
            }
        }

        // Block Change - Blok değişimleri
        else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            Vector3i pos = wrapper.getBlockPosition();

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Block change detected at " +
                    pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        // Ses paketlerini yakala - oyuncunun duydugu TUM sesleri kaydet
        // (mob, ambiyans, hava durumu, blok, entity, muzik vs.)
        else if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            try {
                WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);

                // Ses kaynagi bilgisini al
                com.github.retrooper.packetevents.protocol.sound.Sound sound = wrapper.getSound();
                if (sound == null) return;

                // Ses adini al - birden fazla yontem dene
                String soundName = null;

                // Yontem 1: getName() -> ResourceLocation
                try {
                    Object nameObj = sound.getName();
                    if (nameObj != null) {
                        soundName = nameObj.toString();
                    }
                } catch (Exception ignored) {}

                // Yontem 2: getKey() (bazi PacketEvents versiyonlarinda)
                if (soundName == null) {
                    try {
                        java.lang.reflect.Method getKeyMethod = sound.getClass().getMethod("getKey");
                        Object keyObj = getKeyMethod.invoke(sound);
                        if (keyObj != null) {
                            soundName = keyObj.toString();
                        }
                    } catch (Exception ignored) {}
                }

                // Yontem 3: toString() fallback
                if (soundName == null) {
                    try {
                        String str = sound.toString();
                        // "Sound{name=minecraft:entity.player.hurt}" gibi formattan parse et
                        if (str.contains("name=")) {
                            int start = str.indexOf("name=") + 5;
                            int end = str.indexOf("}", start);
                            if (end < 0) end = str.indexOf(",", start);
                            if (end < 0) end = str.length();
                            soundName = str.substring(start, end).trim();
                        }
                    } catch (Exception ignored) {}
                }

                if (soundName == null || soundName.isEmpty()) return;

                // Namespace ekle
                if (!soundName.contains(":")) {
                    soundName = "minecraft:" + soundName;
                }

                // Konum (PacketEvents 2.x: getEffectPosition fixed-point * 8)
                com.github.retrooper.packetevents.util.Vector3i soundPos = wrapper.getEffectPosition();
                double soundX = soundPos.getX() / 8.0;
                double soundY = soundPos.getY() / 8.0;
                double soundZ = soundPos.getZ() / 8.0;

                // Cok uzak sesler kaydetme (30 blok mesafe, performans icin)
                Location playerLoc = recordedPlayer.getLocation();
                double dx = soundX - playerLoc.getX();
                double dy = soundY - playerLoc.getY();
                double dz = soundZ - playerLoc.getZ();
                if (dx * dx + dy * dy + dz * dz > 900) return; // 30^2

                float volume = wrapper.getVolume();
                float pitch = wrapper.getPitch();

                session.addAction(new SoundAction(soundName, soundX, soundY, soundZ, volume, pitch));
            } catch (Exception e) {
                // Ses paketi parse hatasi
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[RECORDING] Sound capture error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Hareket paketlerini işler
     */
    private void handleMovementPacket(PacketReceiveEvent event, PacketTypeCommon packetType) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPositionTime < 50) { // 50ms = 1 tick
            return; // Spam'i önle
        }

        // Her 100 hareket paketinde bir log (paketlerin yakalandığını doğrulamak için)
        packetCount++;
        if (packetCount % 100 == 0) {
            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Movement packets received: " + packetCount +
                    " | Actions: " + session.getActions().size());
        }

        Location newLocation = null;

        if (packetType.equals(PacketType.Play.Client.PLAYER_POSITION)) {
            WrapperPlayClientPlayerPosition wrapper = new WrapperPlayClientPlayerPosition(event);
            newLocation = new Location(
                    recordedPlayer.getWorld(),
                    wrapper.getPosition().getX(),
                    wrapper.getPosition().getY(),
                    wrapper.getPosition().getZ(),
                    lastLocation.getYaw(),
                    lastLocation.getPitch()
            );
        } else if (packetType.equals(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION)) {
            WrapperPlayClientPlayerPositionAndRotation wrapper = new WrapperPlayClientPlayerPositionAndRotation(event);
            newLocation = new Location(
                    recordedPlayer.getWorld(),
                    wrapper.getPosition().getX(),
                    wrapper.getPosition().getY(),
                    wrapper.getPosition().getZ(),
                    wrapper.getYaw(),
                    wrapper.getPitch()
            );
        } else if (packetType.equals(PacketType.Play.Client.PLAYER_ROTATION)) {
            WrapperPlayClientPlayerRotation wrapper = new WrapperPlayClientPlayerRotation(event);
            newLocation = new Location(
                    recordedPlayer.getWorld(),
                    lastLocation.getX(),
                    lastLocation.getY(),
                    lastLocation.getZ(),
                    wrapper.getYaw(),
                    wrapper.getPitch()
            );
        } else if (packetType.equals(PacketType.Play.Client.PLAYER_FLYING)) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (!wrapper.hasPositionChanged() && !wrapper.hasRotationChanged()) {
                return;
            }
        }

        if (newLocation != null && (lastLocation == null ||
                !lastLocation.getWorld().equals(newLocation.getWorld()) ||
                lastLocation.distance(newLocation) > 0.01 ||
                Math.abs(lastLocation.getYaw() - newLocation.getYaw()) > 1 ||
                Math.abs(lastLocation.getPitch() - newLocation.getPitch()) > 1)) {

            session.addAction(new LocationAction(
                    newLocation.getX(),
                    newLocation.getY(),
                    newLocation.getZ(),
                    newLocation.getYaw(),
                    newLocation.getPitch(),
                    recordedPlayer.isOnGround()
            ));

            // HAREKET KAYDI - Debug log
            if (session.getActions().size() % 50 == 0) { // Her 50 action'da bir log
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Location recorded: " +
                        String.format("%.1f, %.1f, %.1f", newLocation.getX(), newLocation.getY(), newLocation.getZ()) +
                        " | Total actions: " + session.getActions().size());
            }

            lastLocation = newLocation;
            lastPositionTime = currentTime;

            // DEVRE DIŞI: Eski nearby player tracking sistemi
            // Artık NearbyPlayerTracker sınıfı kullanılıyor (periyodik, daha gelişmiş)
            // checkNearbyPlayers();

            // YENİ: Yanma durumu kontrolü
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean isOnFire = recordedPlayer.getFireTicks() > 0;

                if (wasOnFire != isOnFire) {
                    FireAction fireAction = new FireAction(isOnFire, recordedPlayer.getFireTicks());
                    session.addAction(fireAction);
                    wasOnFire = isOnFire;

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Fire state changed: " + isOnFire +
                            " ticks: " + recordedPlayer.getFireTicks());
                }
            });
        }
    }

    /**
     * Equipment değişikliği kaydeder
     */
    private void recordEquipmentItem(ItemStack item, EquipmentAction.EquipmentSlot slot) {
        try {
            EquipmentAction.ItemData itemData = null;

            if (item != null && item.getType() != Material.AIR) {
                // ItemStack'i serialize et
                byte[] itemStackData = ItemSerializer.itemStackToBytes(item);

                // Enchantments
                Map<String, Integer> enchantments = new HashMap<>();
                item.getEnchantments().forEach((enchant, level) -> {
                    enchantments.put(enchant.getKey().getKey(), level);
                });

                // Meta bilgileri
                String displayName = null;
                List<String> lore = null;
                int customModelData = 0;
                boolean unbreakable = false;

                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();

                    if (meta.hasDisplayName()) {
                        displayName = meta.getDisplayName();
                    }

                    if (meta.hasLore()) {
                        lore = meta.getLore();
                    }

                    if (meta.hasCustomModelData()) {
                        customModelData = meta.getCustomModelData();
                    }

                    unbreakable = meta.isUnbreakable();
                }

                itemData = new EquipmentAction.ItemData(
                        item.getType().name(),
                        item.getAmount(),
                        item.getDurability(),
                        displayName,
                        lore,
                        enchantments,
                        itemStackData,
                        customModelData,
                        unbreakable
                );

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Equipment recorded: " + slot + " - " + item.getType().name());
            } else {
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Equipment recorded: " + slot + " - EMPTY");
            }

            session.addAction(new EquipmentAction(slot, itemData));

        } catch (Exception e) {
            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Failed to record equipment: " + e.getMessage());
        }
    }

}