package com.lildan42.mods.tcpcontrollermod2;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TCPControllerMod2.MODID)
public class TCPControllerMod2 {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "tcpcontrollermod2";

    private static final TCPServer SERVER = new TCPServer();

    private static final Queue<Screen> SCREEN_QUEUE = new LinkedList<>();

    private static final double LAG_CHECK_AABB_SIZE = 100.0;
    private static final int MAX_ENTITY_CAPACITY = 500;

    public TCPControllerMod2() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
        LOGGER.info("HELLO FROM PREINIT");
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo(MODID, "helloworld", () -> {
            LOGGER.info("Hello world from the MDK");
            return "Hello world";
        });
    }

    private void processIMC(final InterModProcessEvent event) {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m -> m.messageSupplier().get()).
                collect(Collectors.toList()));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            LOGGER.info("HELLO from Register Block");
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEventBusSubscriber {

        @SubscribeEvent
        public static void onWorldTick(TickEvent.WorldTickEvent evt) {
            if(InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_I)
                    && Minecraft.getInstance().screen == null)
                SCREEN_QUEUE.add(new TCPCommandSettingsScreen());

            evt.world.players().forEach(player -> {
                ForgeEventBusSubscriber.clearExtraEntities(ItemEntity.class, evt.world, player);
                ForgeEventBusSubscriber.clearExtraEntities(FallingBlockEntity.class, evt.world, player);
            });

            SERVER.loop(evt.world);
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent evt) {
            for(Screen screen = SCREEN_QUEUE.poll(); screen != null; screen = SCREEN_QUEUE.poll()) {
                Minecraft.getInstance().setScreen(screen);
            }
        }

        private static <T extends Entity> void clearExtraEntities(Class<T> entityType, Level level, Player player) {
            List<T> entities = level.getEntitiesOfClass(entityType, player.getBoundingBox().inflate(LAG_CHECK_AABB_SIZE));

            for(int i = MAX_ENTITY_CAPACITY; i < entities.size(); i++) {
                entities.get(i).remove(Entity.RemovalReason.KILLED);
            }
        }
    }
}
