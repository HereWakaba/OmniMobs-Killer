package wakaba.hiiro.omnimobskiller.item;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class OmnimobsKillerItem extends Item {

    public static final Predicate<Entity> NON_PLAYERS = entity -> !(entity instanceof Player);
    private static final int COOLDOWN_TICKS = 20;
    private static long lastWipeTick = Long.MIN_VALUE;
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    public OmnimobsKillerItem(Properties p_41383_) {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public Component getName(ItemStack p_41381_) {
        return Component.translatable("点击击杀Omnmobs全体");
    }

    @Override
    public boolean onEntitySwing(ItemStack p_41402_, LivingEntity p_41403_) {
        if (p_41403_.level().isClientSide()) {
            clearClientBossBars();
            return false;
        }
        if (!(p_41403_ instanceof ServerPlayer player)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        long tick = server.getTickCount();
        if (lastWipeTick != Long.MIN_VALUE && tick - lastWipeTick < COOLDOWN_TICKS) {
            return false;
        }
        lastWipeTick = tick;
        wipeAll(server, NON_PLAYERS);
        return false;
    }

    private static void clearClientBossBars() {
        try {
            Minecraft.getInstance().gui.getBossOverlay().reset();
        } catch (Throwable ignored) {
        }
    }

    public static int wipeAll(MinecraftServer server, Predicate<Entity> filter) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> snapshot = new ArrayList<>();
            level.getAllEntities().forEach(snapshot::add);

            for (Entity entity : snapshot) {
                if (entity.isRemoved() || !filter.test(entity)) {
                    continue;
                }
                sweepBossEvents(entity);
                annihilate(level, entity);
                removed++;
            }
            sweepBossEvents(level.getDragonFight());
            sweepBossEvents(level.getRaids());
        }

        for (CustomBossEvent event : server.getCustomBossEvents().getEvents()) {
            event.removeAllPlayers();
        }
        return removed;
    }

    public static void sendRemovalPackets(ServerLevel level, Entity target) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(target.getId());
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static void annihilate(ServerLevel level, Entity entity) {
        int id = entity.getId();
        try {
            var manager = level.entityManager;
            long sectionKey = SectionPos.asLong(entity.blockPosition());

            var section = manager.sectionStorage.getSection(sectionKey);
            if (section != null) {
                ClassInstanceMultiMap<Entity> storage = section.storage;
                for (List<Entity> list : storage.byClass.values()) {
                    list.removeIf(candidate -> candidate == entity);
                }
                manager.removeSectionIfEmpty(sectionKey, section);
            }

            var visible = manager.visibleEntityStorage;
            visible.byId.remove(id);
            visible.byUuid.remove(entity.getUUID());

            manager.knownUuids.remove(entity.getUUID());
            level.entityTickList.active.remove(id);
            level.getChunkSource().chunkMap.removeEntity(entity);

            entity.setRemoved(Entity.RemovalReason.DISCARDED);
        } catch (Throwable t) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        } finally {
            try {
                sendRemovalPackets(level, entity);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sweepBossEvents(Object root) {
        if (root == null) {
            return;
        }
        for (Field field : instanceFields(root.getClass())) {
            Object value = read(field, root);
            drop(value);
            if (value != null && !(value instanceof Entity) && !(value instanceof Level)) {
                for (Field inner : instanceFields(value.getClass())) {
                    drop(read(inner, value));
                }
            }
        }
    }

    private static void drop(Object candidate) {
        if (candidate instanceof ServerBossEvent event) {
            if (!event.isVisible()) {
                event.setVisible(true);
            }
            event.removeAllPlayers();
        }
    }

    private static List<Field> instanceFields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, OmnimobsKillerItem::collectFields);
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    result.add(field);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    private static Object read(Field field, Object holder) {
        try {
            return field.get(holder);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

}
