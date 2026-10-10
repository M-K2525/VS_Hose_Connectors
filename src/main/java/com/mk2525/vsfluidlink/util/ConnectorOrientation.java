package com.mk2525.vsfluidlink.util;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class ConnectorOrientation {
    public static final IntegerProperty ROLL = IntegerProperty.create("roll", 0, 3);
    private static final BooleanProperty UPSIDE_DOWN = BooleanProperty.create("upside_down");

    private ConnectorOrientation() {}

    public static Direction facing(BlockState state) {
        return state.getValue(BlockStateProperties.FACING);
    }

    private static Direction quarterTurn(Direction axis, Direction direction) {
        int dot = axis.getStepX() * direction.getStepX()
                + axis.getStepY() * direction.getStepY()
                + axis.getStepZ() * direction.getStepZ();
        return Direction.fromDelta(
                axis.getStepY() * direction.getStepZ() - axis.getStepZ() * direction.getStepY() + axis.getStepX() * dot,
                axis.getStepZ() * direction.getStepX() - axis.getStepX() * direction.getStepZ() + axis.getStepY() * dot,
                axis.getStepX() * direction.getStepY() - axis.getStepY() * direction.getStepX() + axis.getStepZ() * dot);
    }

    public static Direction rotate(BlockState state, Direction direction) {
        int turns = state.hasProperty(ROLL) ? state.getValue(ROLL) : 0;
        for (int i = 0; i < turns; i++) {
            direction = quarterTurn(facing(state), direction);
        }
        return direction;
    }

    public static Direction.Axis shaftAxis(BlockState state) {
        return modelSide(state).getAxis();
    }

    public static Direction modelSide(BlockState state) {
        Direction side = switch (facing(state)) {
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            case SOUTH -> Direction.WEST;
            default -> Direction.EAST;
        };
        return rotate(state, side);
    }

    public static Direction modelUp(BlockState state) {
        Direction up = switch (facing(state)) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
        if (state.hasProperty(UPSIDE_DOWN) && state.getValue(UPSIDE_DOWN)) {
            up = up.getOpposite();
        }
        return rotate(state, up);
    }

    public static BlockState rotateAroundFace(BlockState state, Direction face) {
        Direction newFacing = quarterTurn(face, facing(state));
        Direction newUp = quarterTurn(face, modelUp(state));
        BlockState oriented = state.setValue(BlockStateProperties.FACING, newFacing);
        // Keep the complete pose, including roll, when the connection direction changes.
        for (int roll = 0; roll < 4; roll++) {
            BlockState candidate = oriented.setValue(ROLL, roll);
            if (modelUp(candidate) == newUp) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot represent connector rotation: " + state);
    }

    public static InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            var level = context.getLevel();
            var pos = context.getClickedPos();
            BlockState rotated = rotateAroundFace(state, context.getClickedFace());
            KineticBlockEntity.switchToBlockState(level, pos, rotated);
            level.invalidateCapabilities(pos);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
