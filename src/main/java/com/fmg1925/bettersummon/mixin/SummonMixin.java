package com.fmg1925.bettersummon.mixin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.RoundingMode;
import java.text.DecimalFormat;

@Mixin(SummonCommand.class)
public class SummonMixin {
    @Unique
    private static final DecimalFormat COORD_FORMAT = new DecimalFormat("##.##");

    static {
        COORD_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    @Inject(method = "register", at = @At("RETURN"))
    private static void addCustomArgument(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, CallbackInfo ci) {
        CommandNode<CommandSourceStack> summonNode = dispatcher.getRoot().getChild("summon");
        if (summonNode == null) return;

        CommandNode<CommandSourceStack> entityNode = summonNode.getChild("entity");
        CommandNode<CommandSourceStack> posNode = entityNode != null ? entityNode.getChild("pos") : null;
        CommandNode<CommandSourceStack> nbtNode = posNode != null ? posNode.getChild("nbt") : null;

        if (nbtNode != null) {
            Command<CommandSourceStack> custom = context2 -> {
                int quantity;

                try {
                    quantity = IntegerArgumentType.getInteger((CommandContext<?>) context2, "quantity");
                } catch (IllegalArgumentException e) {
                    quantity = 1;
                }

                var source = context2.getSource();
                var entityType = ResourceArgument.getSummonableEntityType(context2, "entity");

                Vec3 pos;
                try {
                    pos = Vec3Argument.getVec3(context2, "pos");
                } catch (IllegalArgumentException e) {
                    pos = source.getPosition();
                }

                CompoundTag nbt;
                try {
                    nbt = (CompoundTag) NbtTagArgument.getNbtTag(context2, "nbt");
                } catch (IllegalArgumentException e) {
                    nbt = new CompoundTag();
                }

                for (int i = 0; i < quantity; i++) {
                    SummonCommand.createEntity(source, entityType, pos, nbt, true);
                }

                Vec3 finalPos = pos;
                var coordsStr = " [" + COORD_FORMAT.format(finalPos.x) + ", " + COORD_FORMAT.format(finalPos.y) + ", " + COORD_FORMAT.format(finalPos.z) + "]";
                var greenCoordsStr = Component.literal(coordsStr).withColor(0x00FF00);

                if (quantity > 1) {
                    int finalQuantity = quantity;
                    source.sendSuccess(() -> Component.translatable("commands.summon.success", Component.literal(finalQuantity + " ").append(entityType.getRegisteredName()).append("(s)").append(greenCoordsStr)), true);
                } else {
                    var nameWithCoords = entityType.getRegisteredName() + " " + greenCoordsStr;
                    source.sendSuccess(() -> Component.translatable("commands.summon.success", nameWithCoords), true);
                }

                return quantity;
            };

            var quantityArg = Commands.argument("quantity", IntegerArgumentType.integer(1))
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
}


