package command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import entity.LibrarianNPCEntity;
import entity.NPCDataManager;
import entity.ProfessorNPCEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import registry.EntityRegistry;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = "npcopenai")
public class CommandRegistry {

    private static CompletableFuture<Suggestions> suggestNPCType(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String[] npcs = {"librarian", "professor"}; // Example array of NPC types
        for (String npc : npcs) {
            builder.suggest(npc);
        }
        return builder.buildFuture();
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("spawnnpc")
                        .requires(cs -> cs.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CommandRegistry::suggestNPCType)
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, 12))
                                        .executes(context -> spawnNPC(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "type"),
                                        IntegerArgumentType.getInteger(context, "index")
                                ))
                        )
                        )
        );

        dispatcher.register(
                Commands.literal("findnpc")
                        .requires(cs -> cs.hasPermission(0))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 12))
                                .executes(context -> findNPC(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "index")
                                ))
                        )
        );

        dispatcher.register(
                Commands.literal("clearnpcdata")
                        .requires(cs -> cs.hasPermission(2))
                        .executes(context -> clearNPCData(context.getSource()))
        );
    }

    private static int spawnNPC(CommandSourceStack source, String type, int index) {
        ServerLevel world = source.getLevel();
        if (NPCDataManager.activeNPCs.containsKey(index)) {
            source.sendFailure(new TextComponent("An NPC with this index already exists."));
            return 0;
        }
        Entity npc;
        if (type.equalsIgnoreCase("librarian")) {
            npc = new LibrarianNPCEntity(EntityRegistry.LIBRARIAN_ENTITY.get(), world);
            ((LibrarianNPCEntity) npc).initialize(index); // 初始化实体
        } else {
            npc = new ProfessorNPCEntity(EntityRegistry.PROFESSOR_ENTITY.get(), world);
            ((ProfessorNPCEntity) npc).initialize(index); // 初始化实体
        }
        BlockPos pos = new BlockPos(source.getPosition());
        npc.setPos(pos.getX(), pos.getY(), pos.getZ());
        world.addFreshEntity(npc);
        NPCDataManager.saveOrUpdateNPC(world, index, npc, type);
        source.sendSuccess(new TextComponent("NPC spawned successfully!"), true);
        return Command.SINGLE_SUCCESS;
    }

    // 查找特定索引的 NPC 并给出其位置
    public static int findNPC(CommandSourceStack source, int index) throws CommandSyntaxException {
        // 直接检查是否存在该index的NPC
        if (!NPCDataManager.activeNPCs.containsKey(index)) {
            source.sendFailure(new TextComponent("No NPC found with the given index."));
            return 0; // 返回 0 表示没有找到
        }

        ServerLevel world = source.getLevel();
        UUID uuid = NPCDataManager.getUUIDByIndex(index);
        System.out.println("UUID: " + uuid);
        Entity npc = NPCDataManager.findNPCByIndex(source.getLevel(), index);

        Iterable<Entity> entities = world.getAllEntities();
        for (Entity entity : entities) {
            if (entity instanceof LibrarianNPCEntity || entity instanceof ProfessorNPCEntity) {
                if (entity.getUUID().equals(uuid)) {
                    npc = entity;
                }
            }
        }

        System.out.println(npc);
        if (npc != null) {
            ServerPlayer player = source.getPlayerOrException();
            teleportPlayerToEntity(player, npc, 2.0, source);

            // NPC 找到，发送成功消息，并显示位置
            source.sendSuccess(new TextComponent(String.format("Found NPC at [%s].", npc.blockPosition().toShortString())), true);
        } else {
            // NPC 未找到，可能已被移除
            //source.sendFailure(new TextComponent("NPC is respawned."));
            ServerPlayer player = source.getPlayerOrException();
            BlockPos npcPos = NPCDataManager.getNPCLastPositionByIndex(world, index);
            npc = respawnNPC(source, world, index);
            teleportPlayerToEntity(player, npc, 2.0, source); // 传送到刚刚respawn的NPC位置
        }
        return 1;
    }
    private static void teleportPlayerToEntity(ServerPlayer player, Entity entity, double distance, CommandSourceStack source) {
        BlockPos entityPos = entity.blockPosition();
        double dx = entityPos.getX() - player.getX();
        double dz = entityPos.getZ() - player.getZ();
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;

        float newYaw = angle;
        float newPitch = player.getXRot();

        double rad = Math.toRadians(angle - 90);
        double newX = entityPos.getX() + distance * Math.cos(rad);
        double newZ = entityPos.getZ() + distance * Math.sin(rad);

        player.teleportTo(source.getLevel(), newX, entityPos.getY(), newZ, newYaw, newPitch);
    }

    private static Entity respawnNPC(CommandSourceStack source, ServerLevel world, int index) {
        BlockPos lastKnownPos = NPCDataManager.getNPCLastPositionByIndex(world, index);
        String npcType = NPCDataManager.getNPCTypeByIndex(world,index);  // Assume this method exists and properly retrieves the type

        Entity npc = spawnNPCByType(world, npcType, lastKnownPos, index);
        if (npc == null) {
            source.sendFailure(new TextComponent("Failed to respawn NPC."));
            return null;
        }

        NPCDataManager.saveOrUpdateNPC(world, index, npc, npcType);
        source.sendSuccess(new TextComponent("NPC is respawned at the last known location."), true);
        return npc;
    }

    private static Entity spawnNPCByType(ServerLevel world, String type, BlockPos pos, int index) {
        Entity npc = null;
        switch (type.toLowerCase()) {
            case "librarian":
                npc = new LibrarianNPCEntity(EntityRegistry.LIBRARIAN_ENTITY.get(), world);
                ((LibrarianNPCEntity) npc).initialize(index); // 初始化实体
                break;
            case "professor":
                npc = new ProfessorNPCEntity(EntityRegistry.PROFESSOR_ENTITY.get(), world);
                ((ProfessorNPCEntity) npc).initialize(index); // 初始化实体

                break;
        }
        if (npc != null) {
            npc.setPos(pos.getX(), pos.getY(), pos.getZ());
            world.addFreshEntity(npc);
        }
        return npc;
    }

    private static int clearNPCData(CommandSourceStack source) {
        ServerLevel world = source.getLevel();

        // 移除所有 NPCEntity 类型的实体
        Iterable<Entity> entities = world.getAllEntities();
        for (Entity entity : entities) {

            if (entity instanceof LibrarianNPCEntity || entity instanceof ProfessorNPCEntity) {
                entity.remove(Entity.RemovalReason.DISCARDED); // 或使用 entity.discard() 根据版本
                System.out.println(entity.getUUID());
            }
        }

        // 清空NPC数据
        NPCDataManager.clearAllNPCData(source.getLevel());

        // 发送成功消息
        source.sendSuccess(new TextComponent("All NPC data cleared successfully and all NPCs have been removed."), true);
        return Command.SINGLE_SUCCESS;
    }

}