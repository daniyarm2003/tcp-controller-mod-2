package com.lildan42.mods.tcpcontrollermod2;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class TCPCommandClass {

    private static final double SCATTER_DISTANCE = 5.0;
    private static final double AGGRO_DIST = 100.0;

    @Nullable
    private static <T> T validateArg(String arg, Function<String, T> testFunc) {
        try {
            return testFunc.apply(arg);
        }
        catch(Exception ignored) {
            return null;
        }
    }

    private static int totalInventorySize(Inventory inventory) {
        return inventory.items.size() + inventory.armor.size() + inventory.offhand.size();
    }

    @TCPCommand(id = 0)
    public static String runCommand(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Optional<String> errorMessage = TCPCommandSettings.getInstance().canRunCommand(args);

        if(errorMessage.isPresent())
            return errorMessage.get();

        if(server.getCommands().performCommand(server.createCommandSourceStack(), String.join(" ", args)) == 0)
            return "Command failed to execute!";

        return "Success!";

    }

    @TCPCommand(id = 1)
    public static String sendMessage(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        server.getPlayerList().broadcastMessage(new TextComponent(String.join(" ", args)), ChatType.SYSTEM, UUID.randomUUID());
        return "Message was sent!";
    }

    @TCPCommand(id = 2, maxArgs = 1)
    public static String scatterItems(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        Inventory inventory = player.getInventory();

        for(int i = 0; i < TCPCommandClass.totalInventorySize(inventory); i++) {
            ItemStack itemStack = inventory.removeItemNoUpdate(i);

            if(itemStack.isEmpty())
                continue;

            double theta = 2.0 * Math.PI * Math.random();

            ItemEntity itemEntity = new ItemEntity(level, player.getX() + SCATTER_DISTANCE * Math.cos(theta), player.getEyeY(), player.getZ() + SCATTER_DISTANCE * Math.sin(theta), itemStack);

            itemEntity.setDefaultPickUpDelay();
            itemEntity.setOwner(player.getUUID());

            itemEntity.setDeltaMovement(Vec3.ZERO);
            itemEntity.hurtMarked = true;

            if(!level.isClientSide)
                level.addFreshEntity(itemEntity);
        }

        return "Items Scattered!";
    }

    @TCPCommand(id = 3, minArgs = 4, maxArgs = 4)
    public static String updateVelocity(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        final Vec3 up = new Vec3(0.0, 1.0, 0.0);

        Vec3 forward = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        Vec3 right = forward.cross(up).normalize();

        final int dimensions = 3;

        double[] velInputs = new double[dimensions];

        for(int i = 0; i < dimensions; i++) {
            Double parsedNum = TCPCommandClass.validateArg(args[i + 1], Double::parseDouble);

            if(parsedNum == null)
                return "Argument %d must be a number".formatted(i + 2);

            velInputs[i] = parsedNum;
        }

        Vec3 vel = right.multiply(velInputs[0], velInputs[0], velInputs[0])
                .add(up.multiply(velInputs[1], velInputs[1], velInputs[1])
                        .add(forward.multiply(velInputs[2], velInputs[2], velInputs[2])));

        player.setDeltaMovement(vel);
        player.hurtMarked = true;

        return "Velocity Updated!";
    }

    @TCPCommand(id = 4, maxArgs = 1)
    public static String shuffleItems(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        Inventory inventory = player.getInventory();
        int size = TCPCommandClass.totalInventorySize(inventory);

        for(int i = 0; i < size - 1; i++) {
            int swapWith = level.random.nextInt(i, size);

            ItemStack tmp = inventory.removeItemNoUpdate(i);

            inventory.setItem(i, inventory.getItem(swapWith).copy());
            inventory.setItem(swapWith, tmp);
        }

        return "Inventory Shuffled!";
    }

    @TCPCommand(id = 5, maxArgs = 1)
    public static String aggroMobs(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AGGRO_DIST))
                .forEach(mob -> {
                    mob.setTarget(player);
                    mob.setAggressive(true);
                });

        return "Aggroed nearby mobs!";
    }

    @TCPCommand(id = 6, maxArgs = 1)
    public static String healPlayer(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        float deltaHealth = player.getMaxHealth() - player.getHealth();
        player.setHealth(player.getMaxHealth());

        return "Healed player by %.2f points!".formatted(deltaHealth);
    }

    @TCPCommand(id = 7, maxArgs = 1)
    public static String permanentFire(Level level, String[] args) {
        MinecraftServer server = Objects.requireNonNull(level.getServer());

        Player player;
        if((player = TCPCommandClass.validateArg(args[0], server.getPlayerList()::getPlayerByName)) == null)
            return "Player %s does not exist".formatted(args[0]);

        player.setRemainingFireTicks(Integer.MAX_VALUE);

        return "Player set on fire!";
    }
}
