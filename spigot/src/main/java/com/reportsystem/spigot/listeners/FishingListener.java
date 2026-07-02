package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.FishingAction;
import com.reportsystem.common.replay.actions.InteractEntityAction;
import com.reportsystem.common.replay.actions.UseItemAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FishingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public FishingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        // Kayıt yoksa hiçbir şey yapma
        if (session == null) return;

        FishHook hook = event.getHook();
        FishingAction.FishingState state = null;

        switch (event.getState()) {
            case FISHING:
                // Olta kullanma animasyonu ekle
                session.addAction(new UseItemAction(
                        UseItemAction.UseType.FISHING_ROD,
                        0,
                        true, // main hand
                        true  // started
                ));

                // CAST için hook'un VELOCITY'sini kaydet (konum değil!)
                // hookX/Y/Z alanlarına velocity yazılır, replay'de gerçek fırlatma yönü kullanılır
                org.bukkit.util.Vector vel = hook.getVelocity();
                session.addAction(new FishingAction(
                        FishingAction.FishingState.CAST,
                        vel.getX(), vel.getY(), vel.getZ()
                ));

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Fishing CAST with velocity: " +
                        String.format("%.3f, %.3f, %.3f", vel.getX(), vel.getY(), vel.getZ()));
                // state null bırak — alttaki default kayıt çalışmasın
                break;

            case CAUGHT_FISH:
                state = FishingAction.FishingState.CAUGHT;

                // Yakalanan item'ı serialize et
                if (event.getCaught() instanceof Item) {
                    Item caughtItem = (Item) event.getCaught();
                    org.bukkit.inventory.ItemStack itemStack = caughtItem.getItemStack();
                    String caughtItemData = com.reportsystem.spigot.utils.ItemSerializer.serializeItemStack(itemStack);

                    // FishingAction'ı caught item ile kaydet
                    FishingAction action = new FishingAction(
                            state,
                            hook.getLocation().getX(),
                            hook.getLocation().getY(),
                            hook.getLocation().getZ(),
                            caughtItemData
                    );
                    session.addAction(action);

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Fishing caught: " + itemStack.getType().name() +
                            " (amount: " + itemStack.getAmount() + ")");
                    return; // Early return to avoid duplicate action
                }
                break;

            case CAUGHT_ENTITY:
                // Entity yakalandıysa
                if (event.getCaught() != null) {
                    if (event.getCaught() instanceof Item) {
                        state = FishingAction.FishingState.CAUGHT;
                    } else {
                        // Entity çekme (koyun vb.)
                        state = FishingAction.FishingState.REEL_IN;

                        // Entity'yi kaydet
                        InteractEntityAction entityAction = new InteractEntityAction(
                                InteractEntityAction.InteractionType.RIGHT_CLICK,
                                event.getCaught().getUniqueId(),
                                event.getCaught().getType().name() + "_FISHING_CAUGHT",
                                event.getCaught().getLocation().getX(),
                                event.getCaught().getLocation().getY(),
                                event.getCaught().getLocation().getZ()
                        );
                        session.addAction(entityAction);

                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Entity caught with fishing rod: " + event.getCaught().getType());
                    }
                } else {
                    state = FishingAction.FishingState.CAUGHT;
                }
                break;

            case REEL_IN:
            case IN_GROUND:
                state = FishingAction.FishingState.REEL_IN;

                // Olta kullanma bitişi
                session.addAction(new UseItemAction(
                        UseItemAction.UseType.FISHING_ROD,
                        0,
                        true, // main hand
                        false // ended
                ));
                break;

            case FAILED_ATTEMPT:
            case BITE:
                state = FishingAction.FishingState.FAILED;
                break;
        }

        if (state != null) {
            FishingAction action = new FishingAction(
                    state,
                    hook.getLocation().getX(),
                    hook.getLocation().getY(),
                    hook.getLocation().getZ()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Fishing action: " + state);
        }
    }
}