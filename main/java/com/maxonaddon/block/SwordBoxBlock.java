package com.maxonaddon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;

public class SwordBoxBlock extends BaseEntityBlock {
    public static final MapCodec<SwordBoxBlock> CODEC = simpleCodec(SwordBoxBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // Свойство, которое определяет, является ли этот блок второй половинкой (заглушкой)
    public static final BooleanProperty DUMMY = BooleanProperty.create("dummy");

    public SwordBoxBlock(Properties properties) {
        super(properties.strength(2.5F).sound(SoundType.WOOD).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(DUMMY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, DUMMY);
    }

    // === ФИЗИКА И ОТОБРАЖЕНИЕ ===

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Теперь обе половинки - это ровно 1 блок 16x16, но высотой 8 (полублок)
        return Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Блок-заглушка ничего не рендерит (она невидимая), а главный блок рендерит 3D модель GeckoLib
        return state.getValue(DUMMY) ? RenderShape.INVISIBLE : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Заглушке не нужен инвентарь, только главной части
        if (state.getValue(DUMMY)) return null;
        return new com.maxonaddon.block.entity.SwordBoxBlockEntity(pos, state);
    }

    // === ЛОГИКА УСТАНОВКИ ДВУХ БЛОКОВ ===

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos dummyPos = getDummyPos(context.getClickedPos(), facing);

        // Проверяем, свободна ли соседняя клетка для второго блока!
        if (context.getLevel().getBlockState(dummyPos).canBeReplaced(context)) {
            return this.defaultBlockState().setValue(FACING, facing);
        }
        return null; // Если места нет, не даем поставить ящик
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            // При установке главного блока, автоматически ставим заглушку в соседнюю клетку
            BlockPos dummyPos = getDummyPos(pos, state.getValue(FACING));
            level.setBlock(dummyPos, state.setValue(DUMMY, true), 3);
        }
    }

    // === ЛОГИКА РАЗРУШЕНИЯ ДВУХ БЛОКОВ ===

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Если ломаем одну половину, вторая ломается тоже
        BlockPos otherPos = state.getValue(DUMMY) ? getMainPos(pos, state.getValue(FACING)) : getDummyPos(pos, state.getValue(FACING));
        BlockState otherState = level.getBlockState(otherPos);

        if (otherState.is(this) && otherState.getValue(DUMMY) != state.getValue(DUMMY)) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 3);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Вещи выпадают ТОЛЬКО из главной части
            if (!state.getValue(DUMMY)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof com.maxonaddon.block.entity.SwordBoxBlockEntity box && state.getValue(OPEN)) {
                    Containers.dropContents(level, pos, box);
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    // === ВЗАИМОДЕЙСТВИЕ (ПКМ И ЛКМ) ===

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(state, level, pos, player) == InteractionResult.PASS
                ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(state, level, pos, player);
    }

    /**
     * Вся логика клика по коробке в одном месте: открыть/закрыть крышку (с шифтом),
     * открыть инвентарь или подобрать закрытую коробку.
     * <p>
     * Специально НЕ смотрит на то, что у игрока в руках. Ванильный маршрут взаимодействия
     * разводит клик по двум методам — useWithoutItem вызывается только когда игрок не крадётся
     * или обе руки пусты, а useItemOn — только для той руки, в которой лежит предмет. Из-за
     * этого поведение коробки зависело от того, чем заняты руки, и её приходилось открывать
     * с пустыми. Оба метода теперь ведут сюда, и этот же метод дёргается из UseBlockCallback
     * (см. MaxonDaotAddon) ещё до разбора рук — так клик отрабатывает одинаково всегда.
     */
    public InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
        // Клик по блоку-заглушке перенаправляем на главную часть коробки.
        if (state.getValue(DUMMY)) {
            BlockPos mainPos = getMainPos(pos, state.getValue(FACING));
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(this) && !mainState.getValue(DUMMY)) {
                return this.interact(mainState, level, mainPos, player);
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof com.maxonaddon.block.entity.SwordBoxBlockEntity boxEntity)) return InteractionResult.PASS;

        boolean isOpen = state.getValue(OPEN);

        if (player.isShiftKeyDown()) {
            if (isOpen) {
                level.setBlock(pos, state.setValue(OPEN, false), 3);
                boxEntity.triggerAnim("controller", "down_door.model.new2");
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                level.setBlock(pos, state.setValue(OPEN, true), 3);
                boxEntity.triggerAnim("controller", "up_door.model.new");
                level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                checkDeliverySeal(player, boxEntity);
            }
            return InteractionResult.SUCCESS;
        }

        if (isOpen) {
            player.openMenu(boxEntity);
        } else {
            ItemStack dropStack = new ItemStack(this);
            boxEntity.saveToItem(dropStack, level.registryAccess());
            popResource(level, pos, dropStack);

            // Удаляем обе половинки из мира
            BlockPos dummyPos = getDummyPos(pos, state.getValue(FACING));
            level.removeBlock(dummyPos, false);
            level.removeBlock(pos, false);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        // Если ударили по заглушке - перенаправляем удар на главную коробку
        if (state.getValue(DUMMY)) {
            BlockPos mainPos = getMainPos(pos, state.getValue(FACING));
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(this) && !mainState.getValue(DUMMY)) {
                this.attack(mainState, level, mainPos, player);
            }
            return;
        }

        if (!level.isClientSide && state.getValue(OPEN)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.maxonaddon.block.entity.SwordBoxBlockEntity boxEntity) {
                level.setBlock(pos, state.setValue(OPEN, false), 3);
                boxEntity.triggerAnim("controller", "down_door.model.new2");
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        super.attack(state, level, pos, player);
    }

    // === ПОМОЩНИКИ ДЛЯ КООРДИНАТ ===

    private BlockPos getDummyPos(BlockPos mainPos, Direction facing) {
        switch (facing) {
            case NORTH: return mainPos.east();
            case SOUTH: return mainPos.west();
            case EAST: return mainPos.south();
            case WEST: return mainPos.north();
            default: return mainPos.east();
        }
    }

    private BlockPos getMainPos(BlockPos dummyPos, Direction facing) {
        switch (facing) {
            case NORTH: return dummyPos.west();
            case SOUTH: return dummyPos.east();
            case EAST: return dummyPos.north();
            case WEST: return dummyPos.south();
            default: return dummyPos.west();
        }
    }

    /**
     * Квест "Доставить припасы" (см. com.maxonaddon.DeliveryQuestHandler): если это ЗАПЕЧАТАННЫЙ
     * ящик-задание, а его открывает (SHIFT+ПКМ) именно тот игрок, которому его выдали — пломба
     * считается сорванной, задание проваливается. Ставим флаг сразу, чтобы повторные щелчки по
     * уже открытому ящику не проваливали задание ещё раз (оно к тому моменту уже неактивно).
     */
    private static void checkDeliverySeal(Player player, com.maxonaddon.block.entity.SwordBoxBlockEntity boxEntity) {
        if (!boxEntity.isMaxonDeliveryBox() || boxEntity.isMaxonSealBroken()) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        if (!boxEntity.getMaxonDeliveryOwnerUuid().equals(player.getUUID().toString())) return;

        boxEntity.setMaxonSealBroken(true);
        com.maxonaddon.DeliveryQuestHandler.onSealBroken(serverPlayer);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
}