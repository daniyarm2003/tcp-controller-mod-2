package com.lildan42.mods.tcpcontrollermod2;

import net.minecraft.client.Minecraft;

import java.util.*;

public class CommandValidators {

    @CommandValidator(command = "execute")
    public static Optional<String> validateExecute(TCPCommandSettings settings, List<String> args) {
        int runIndex = args.indexOf("run");

        if(runIndex == -1 || runIndex == args.size() - 1)
            return Optional.empty();

        return settings.canRunCommand(args.subList(runIndex + 1, args.size()).toArray(String[]::new));
    }

    @CommandValidator(command = "effect", minArgs = 2)
    public static Optional<String> validateEffect(TCPCommandSettings settings, List<String> args) {
        if(args.size() < 3)
            return Optional.empty();

        String effect = args.get(2);

        if(!settings.isEffectEnabled(effect))
            return Optional.of("Effect \"%s\" is disabled".formatted(effect));

        if(args.size() >= 5) {
            try {
                int amplifier = Integer.parseInt(args.get(4));
                int maxAmplifier = (int) settings.maxEffectStrengthOption.get(Minecraft.getInstance().options);

                if(amplifier > maxAmplifier)
                    return Optional.of("Effect amplifier cannot be greater than %d".formatted(maxAmplifier));
            }
            catch(NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    @CommandValidator(command = "summon", minArgs = 1)
    public static Optional<String> validateSummon(TCPCommandSettings settings, List<String> args) {
        String entity = args.get(0);

        if(!settings.isEntityEnabled(entity))
            return Optional.of("Summoning entities of type \"%s\" is not allowed".formatted(entity));

        return Optional.empty();
    }

    @CommandValidator(command = "fill", minArgs = 7)
    public static Optional<String> validateFill(TCPCommandSettings settings, List<String> args) {
        return CommandValidators.validateBlock(settings, args.get(6));
    }

    @CommandValidator(command = "setblock", minArgs = 4)
    public static Optional<String> validateSetBlock(TCPCommandSettings settings, List<String> args) {
        return CommandValidators.validateBlock(settings, args.get(3));
    }

    private static Optional<String> validateBlock(TCPCommandSettings settings, String block) {
        return !settings.unbreakableBlocksEnabled && settings.isUnbreakableBlock(block) ?
                Optional.of("Unbreakable blocks are not allowed") : Optional.empty();
    }
}
