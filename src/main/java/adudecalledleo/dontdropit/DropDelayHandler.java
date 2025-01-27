package adudecalledleo.dontdropit;

import adudecalledleo.dontdropit.config.DelayActivationMode;
import adudecalledleo.dontdropit.config.FavoredChecker;
import adudecalledleo.dontdropit.config.ModConfig;
import adudecalledleo.dontdropit.duck.HandledScreenHooks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import static adudecalledleo.dontdropit.ModKeyBindings.keyDropStack;
import static adudecalledleo.dontdropit.ModKeyBindings.keyToggleDropDelay;

public class DropDelayHandler {
    private static long dropDelayCounter;
    private static ItemStack currentStack;
    private static boolean wasDropStackDown;
    private static boolean wasToggleDelayDown = false;
    private static DelayActivationMode originalDelayActivationMode = null;

    public static void reset() {
        dropDelayCounter = 0;
        currentStack = ItemStack.EMPTY;
        wasDropStackDown = false;
    }

    static {
        reset();
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            reset();
            wasToggleDelayDown = false;
            return;
        }
        if (ModKeyBindings.isDown(keyToggleDropDelay)) {
            if (!wasToggleDelayDown) {
                wasToggleDelayDown = true;
                ModConfig config = ModConfig.get();
                if (originalDelayActivationMode == null) {
                    originalDelayActivationMode = config.dropDelay.mode;
                    config.dropDelay.mode = DelayActivationMode.DISABLED;
                } else {
                    config.dropDelay.mode = originalDelayActivationMode;
                    originalDelayActivationMode = null;
                }
                ModConfig.save();
            }
        } else
            wasToggleDelayDown = false;
        if (client.currentScreen != null) {
            if (client.currentScreen instanceof HandledScreenHooks)
                tickOnHandledScreen(client, (HandledScreenHooks) client.currentScreen);
        } else
            tickNormally(client);
    }

    private static void tickNormally(MinecraftClient client) {
        if (client.player == null)
            return;
        ItemStack stack = client.player.getInventory().getMainHandStack();
        if (ModConfig.get().dropDelay.mode.isEnabled(stack)) {
            if (client.player.isSpectator()) {
                reset();
                return;
            }
            doDropProgress(client, stack, entireStack -> {
                if (client.player.dropSelectedItem(entireStack))
                    client.player.swingHand(Hand.MAIN_HAND);
            });
        } else {
            reset();
            while (client.options.keyDrop.wasPressed()) {
                if (FavoredChecker.isStackFavored(stack))
                    continue;
                if (!client.player.isSpectator() && client.player.dropSelectedItem(ModKeyBindings.isDown(keyDropStack)))
                    client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private static void tickOnHandledScreen(MinecraftClient client, HandledScreenHooks screenHooks) {
        if (client.player == null)
            return;
        ItemStack stack = screenHooks.dontdropit_getSelectedStack();
        if (ModConfig.get().dropDelay.mode.isEnabled(stack)) {
            if (client.player.isSpectator() || !screenHooks.dontdropit_canDrop()) {
                reset();
                return;
            }
            doDropProgress(client, stack, screenHooks::dontdropit_drop);
        }
        else
            reset();
    }

    @FunctionalInterface
    private interface DropAction {
        void drop(boolean entireStack);
    }

    private static void doDropProgress(MinecraftClient client, ItemStack stack, DropAction dropAction) {
        if (ModKeyBindings.isDown(client.options.keyDrop)) {
            if (dropDelayCounter < getCounterMax()) {
                boolean isDropStackDown = ModKeyBindings.isDown(keyDropStack) && stack.getCount() > 1;
                if (dropDelayCounter == 0)
                    wasDropStackDown = isDropStackDown;
                else if (wasDropStackDown != isDropStackDown) {
                    dropDelayCounter = 0;
                    return;
                }
                if (!FavoredChecker.canDropStack(stack)) {
                    dropDelayCounter = 0;
                    return;
                }
                if (dropDelayCounter > 0 && stack != currentStack)
                    dropDelayCounter = -1;
                currentStack = stack;
                dropDelayCounter++;
            } else {
                dropDelayCounter = ModConfig.get().dropDelay.doDelayOnce ? getCounterMax() : 0;
                dropAction.drop(wasDropStackDown);
            }
        } else
            reset();
    }

    public static long getCounter() {
        return dropDelayCounter;
    }

    public static long getCounterMax() {
        return ModConfig.get().dropDelay.ticks;
    }

    public static ItemStack getCurrentStack() {
        return currentStack;
    }

    public static boolean isDroppingEntireStack() {
        return wasDropStackDown;
    }
}
