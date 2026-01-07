package com.fmg1925.bettersummon.mixin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.sun.jdi.connect.Connector;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.SummonCommand;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.RoundingMode;
import java.text.DecimalFormat;

@Mixin(SummonCommand.class)
public class SummonMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private static void addCustomArgument(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CallbackInfo ci) {
        CommandNode<ServerCommandSource> summonNode = dispatcher.getRoot().getChild("summon");
        if (summonNode == null) return;

        CommandNode<ServerCommandSource> entityNode = summonNode.getChild("entity");
        CommandNode<ServerCommandSource> posNode = entityNode != null ? entityNode.getChild("pos") : null;
        CommandNode<ServerCommandSource> nbtNode = posNode != null ? posNode.getChild("nbt") : null;

        if (nbtNode != null) {
            Command<ServerCommandSource> custom = context -> {
                int quantity;

                try {
                    quantity = IntegerArgumentType.getInteger(context, "quantity");
                } catch (IllegalArgumentException e) {
                    quantity = 1;
                }

                var source = context.getSource();
                var entityType = RegistryEntryReferenceArgumentType.getSummonableEntityType(context, "entity");

                Vec3d pos;
                try {
                    pos = Vec3ArgumentType.getVec3(context, "pos");
                } catch (IllegalArgumentException e) {
                    pos = source.getPosition();
                }

                NbtCompound nbt;
                try {
                    nbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
                } catch (IllegalArgumentException e) {
                    nbt = new NbtCompound();
                }

                for (int i = 0; i < quantity; i++) {
                    SummonCommand.summon(source, entityType, pos, nbt, true);
                }

                Vec3d finalPos = pos;
                var coordsStr = " [" + COORD_FORMAT.format(finalPos.x) + ", " + COORD_FORMAT.format(finalPos.y) + ", " + COORD_FORMAT.format(finalPos.z) + "]";
                var greenCoordsStr = Text.literal(coordsStr).formatted(Formatting.GREEN);

                if (quantity > 1) {
                    int finalQuantity = quantity;
                    source.sendFeedback(() -> Text.translatable("commands.summon.success", Text.literal(finalQuantity + " ").append(entityType.value().getName()).append("(s)").append(greenCoordsStr)), true);
                } else {
                    var nameWithCoords = entityType.value().getName().copy().append(greenCoordsStr);
                    source.sendFeedback(() -> Text.translatable("commands.summon.success", nameWithCoords), true);
                }

                return quantity;
            };

            var quantityArg = CommandManager.argument("quantity", IntegerArgumentType.integer(1))
                    .executes(custom)
                    .build();

            try {
                var cmdField = CommandNode.class.getDeclaredField("command");
                cmdField.setAccessible(true);

                entityNode.addChild(quantityArg);
                cmdField.set(entityNode, custom);
                posNode.addChild(quantityArg);
                cmdField.set(posNode, custom);
                nbtNode.addChild(quantityArg);
                cmdField.set(nbtNode, custom);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }
    }

    private static final DecimalFormat COORD_FORMAT = new DecimalFormat("##.##");

    static {
        COORD_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }
}


