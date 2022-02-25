package com.lildan42.mods.tcpcontrollermod2;

import net.minecraft.client.ProgressOption;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TCPCommandSettings {
    private static final String[] COMMAND_LIST = { "kill", "gamemode", "clear", "gamerule" };
    private static final String[] EFFECT_LIST = {
            "minecraft:instant_damage", "minecraft:wither", "minecraft:regeneration", "minecraft:saturation"
    };
    private static final String[] ENTITY_LIST = {
            "minecraft:lightning_bolt", "minecraft:wither", "minecraft:tnt"
    };
    private static final String[] UNBREAKABLE_BLOCKS = {
            "minecraft:bedrock", "minecraft:barrier", "minecraft:command_block", "minecraft:repeating_command_block",
            "minecraft:chain_command_block"
    };

    private static TCPCommandSettings instance;

    private final HashMap<String, Boolean> enabledCommands, enabledEffects, enabledEntities;

    private double maxEffectStrength = 255.0;
    public boolean unbreakableBlocksEnabled = true;

    public final ProgressOption maxEffectStrengthOption =
            new ProgressOption("gui.settings.max_effect_strength", 0.0, 255.0, 0.5F,
                    opts -> this.maxEffectStrength,
                    (opts, value) -> this.maxEffectStrength = value,
                    (opts, curOption) -> CommonComponents.optionNameValue(
                            new TranslatableComponent("gui.settings.max_effect_strength"),
                            new TextComponent(String.valueOf((int)this.maxEffectStrength + 1))
                    ));

    private TCPCommandSettings() {
        this.enabledCommands = this.initAbilityMap(COMMAND_LIST);
        this.enabledEffects = this.initAbilityMap(EFFECT_LIST);
        this.enabledEntities = this.initAbilityMap(ENTITY_LIST);
    }

    private HashMap<String, Boolean> initAbilityMap(String[] valueArray) {
        return new HashMap<>(Arrays.stream(valueArray).collect(Collectors.toMap(Function.identity(), cmd -> true)));
    }

    private boolean isValueEnabled(HashMap<String, Boolean> map, String value, boolean withMCNamespace) {
        return map.getOrDefault(value, !withMCNamespace || map.getOrDefault("minecraft:" + value, true));
    }

    public boolean isCommandEnabled(String command) {
        return this.isValueEnabled(this.enabledCommands, command, false);
    }

    public boolean isEffectEnabled(String effect) {
        return this.isValueEnabled(this.enabledEffects, effect, true);
    }

    public boolean isEntityEnabled(String entity) {
        return this.isValueEnabled(this.enabledEntities, entity, true);
    }

    public boolean isUnbreakableBlock(String block) {
        for(String unbreakable : UNBREAKABLE_BLOCKS) {
            if(unbreakable.equals(block) || unbreakable.equals("minecraft:" + block)) return true;
        }

        return false;
    }

    public Optional<String> canRunCommand(String[] args) {
        if(args == null || args.length == 0)
            throw new IllegalArgumentException("Cannot validate empty command");

        String command = args[0];
        String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);

        if(!this.isCommandEnabled(command))
            return Optional.of("Command \"%s\" is disabled.".formatted(command));

        return this.validateCommand(command, cmdArgs);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> validateCommand(String command, String[] args) {
        for(Method method : CommandValidators.class.getMethods()) {
            CommandValidator validator = method.getAnnotation(CommandValidator.class);

            if(validator == null)
                continue;

            Class<?>[] params = method.getParameterTypes();

            if(params.length != 2 || !params[0].isAssignableFrom(TCPCommandSettings.class)
                    || !params[1].isAssignableFrom(List.class)
                    || !method.getReturnType().isAssignableFrom(Optional.class))
                throw new UnsupportedOperationException("Invalid command validator.");

            if(validator.command().equals(command)) {
                if(args.length < validator.minArgs())
                    return Optional.of("Expected at least %d arguments (Found %d)".formatted(validator.minArgs(), args.length));

                try {
                    return (Optional<String>) method.invoke(null, this, Arrays.stream(args).toList());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return Optional.empty();
    }

    public Stream<String> getCommandStream() {
        return this.enabledCommands.keySet().stream();
    }

    public Stream<String> getEffectStream() {
        return this.enabledEffects.keySet().stream();
    }

    public Stream<String> getEntityStream() {
        return this.enabledEntities.keySet().stream();
    }

    private void setMapValueEnabled(HashMap<String, Boolean> map, String value, boolean enabled) {
        if(map.replace(value, enabled) == null)
            throw new IllegalArgumentException("Values can only be set for: %s (Got %s)".formatted(String.join(", ", map.keySet()), value));
    }

    public void setCommandEnabled(String command, boolean enabled) {
        this.setMapValueEnabled(this.enabledCommands, command, enabled);
    }

    public void setEffectEnabled(String effect, boolean enabled) {
        this.setMapValueEnabled(this.enabledEffects, effect, enabled);
    }

    public void setEntityEnabled(String entity, boolean enabled) {
        this.setMapValueEnabled(this.enabledEntities, entity, enabled);
    }

    public static TCPCommandSettings getInstance() {
        if(instance == null)
            instance = new TCPCommandSettings();

        return instance;
    }
}
