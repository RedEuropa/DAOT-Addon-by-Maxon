package com.maxonaddon.block;

import com.maxonaddon.MaxonDaotAddon;
import com.mojang.serialization.MapCodec;
import daot.IceburstFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;

public class GasCisternaBlock extends BaseEntityBlock {
    public static final MapCodec<GasCisternaBlock> CODEC = simpleCodec(GasCisternaBlock::new);

    // Свойства блока
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT; // <-- Добавили свойство LIT

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 30.0, 14.0);

    public GasCisternaBlock(Properties properties) {
        super(properties
                .strength(3.5F, 3.5F) // Твердость и сопротивление печки
                .requiresCorrectToolForDrops() // Обязательно требовать кирку
                .sound(SoundType.METAL) // Металлические звуки и партиклы
        );

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        // ВЫЗЫВАЕМ НАШ УСКОРЕННЫЙ МЕТОД
        return createTickerHelper(type, MaxonDaotAddon.GAS_CISTERNA_ENTITY,
                com.maxonaddon.block.entity.GasCisternaBlockEntity::serverTickCisterna
        );
    }

    // 2. ПРИ РАЗРУШЕНИИ БЛОКА
    //
    // Раньше здесь вручную высыпался инвентарь (Containers.dropContents) — лёд/баллон падали
    // на землю, а storedGas (запас газа в баке) просто пропадал: он жил только в NBT блок-сущности,
    // которая уничтожается вместе с блоком, и ни во что не копировался.
    //
    // Теперь сохранение содержимого И газа целиком отдано loot-таблице блока
    // (data/maxon-daot-addon/loot_table/blocks/gas_cisterna.json, функция
    // minecraft:copy_components с source: block_entity, include: minecraft:block_entity_data) —
    // тот же самый стандартный ванильный механизм, которым шалкер-боксы переживают разрушение
    // со всем содержимым. Она копирует ПОЛНЫЙ NBT блок-сущности (наш saveAdditional: и
    // "StoredGas", и слоты инвентаря) в выпавший предмет, а при установке BlockItem
    // восстанавливает его автоматически — без единой строчки нашего кода на приём.
    // Ручной Containers.dropContents теперь дублировал бы содержимое (и на земле, и в предмете),
    // поэтому убран.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof IceburstFurnaceBlockEntity) {
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }


    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    // Регистрируем ОБА свойства в блоке
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT); // <-- Добавили LIT в регистрацию
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Кость "forward" модели должна смотреть НА игрока в момент установки. Раньше FACING
        // брался из context.getHorizontalDirection() — это направление ВЗГЛЯДА игрока (куда
        // повёрнута камера), а не то, где он физически стоит. Обычно оба совпадают (игрок
        // смотрит на блок, который сам же ставит), но при установке сбоку/из другого угла
        // это расходится. Берём направление от позиции блока К игроку напрямую — так
        // гарантированно смотрит на него, а не туда, куда он в этот момент смотрел.
        Direction facing = context.getHorizontalDirection().getOpposite();
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        if (player != null) {
            net.minecraft.core.BlockPos pos = context.getClickedPos();
            double dx = player.getX() - (pos.getX() + 0.5);
            double dz = player.getZ() - (pos.getZ() + 0.5);
            Direction towardsPlayer = Direction.getNearest(dx, 0.0, dz);
            if (towardsPlayer.getAxis().isHorizontal()) {
                facing = towardsPlayer;
            }
        }

        // При установке блок выключен по умолчанию.
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LIT, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.maxonaddon.block.entity.GasCisternaBlockEntity(pos, state);
    }


    @Override

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.maxonaddon.block.entity.GasCisternaBlockEntity cisterna) {

                // === ЛОГИКА ПЕРЕЗАРЯДКИ ГАЗА (SHIFT + ПКМ) ===
                // ВАЖНО: газ хранится в NBT самого предмета-баллона (GasCanisterItem) и НЕ
                // зависит от LIT — LIT это только "сейчас идёт подзарядка баллона льдом"
                // (см. serverTickCisterna), а сам газ никак не сбрасывается, когда лёд
                // заканчивается и LIT гаснет. Поэтому здесь сознательно НЕТ проверки LIT —
                // забор газа работает всегда, пока в баллоне реально есть газ (см. ниже,
                // почему без него это выглядело как "работает только когда включено").
                if (player.isShiftKeyDown()) {
                    ItemStack gear = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
                    boolean isOdm = gear.getItem() instanceof daot.ODMGearItem;
                    boolean isApg = gear.getItem() instanceof daot.ODMAPGItem;

                    if (!isOdm && !isApg) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.gas_cisterna.no_gear").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return InteractionResult.SUCCESS;
                    }

                    // АПГ вмещает вдвое больше газа, чем обычный УПМ (1000 против 500,
                    // см. ODMAPGItem.MAX_GAS / ODMGearItem.MAX_GAS в исходниках). Важно:
                    // это не только другой предел needed — у ODMGearItem.setGas(...) лимит
                    // ЗАШИТ ВНУТРИ метода как [0, 500], поэтому вызов его для АПГ обрезал бы
                    // газ до 500, даже если maxGas тут посчитать правильно. Нужно брать
                    // get/setGas именно из ODMAPGItem, когда на игроке АПГ.
                    int currentGas = isApg ? daot.ODMAPGItem.getGas(gear) : daot.ODMGearItem.getGas(gear);
                    int maxGas = isApg ? 1000 : 500;
                    int needed = maxGas - currentGas;

                    if (needed <= 0) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.gas_cisterna.gear_full").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                        return InteractionResult.SUCCESS;
                    }

                    // ВАЖНО: раньше здесь проверялось наличие баллона в слоте 2 и газ
                    // забирался из его NBT — то есть "есть ли газ" фактически подменялось
                    // "стоит ли баллон", а сама цистерна не хранила запас. Теперь источник
                    // истины — внутренний бак самой цистерны (storedGas), баллон в слоте
                    // для прямой заправки игрока больше не нужен.
                    if (cisterna.getStoredGas() <= 0) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.gas_cisterna.canister_empty").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return InteractionResult.SUCCESS;
                    }

                    // Забираем газ из бака цистерны
                    int toTake = cisterna.takeGas(needed);
                    if (isApg) {
                        daot.ODMAPGItem.setGas(gear, currentGas + toTake);
                    } else {
                        daot.ODMGearItem.setGas(gear, currentGas + toTake);
                    }

                    // Позиционируем игрока у кости "forward" модели (gas_cisterna.geo.json),
                    // а не просто "в клетке рядом с блоком по FACING". Локальный pivot кости —
                    // [10, 1, 4] в юнитах Blockbench (16 = 1 блок), кость статична (не анимируется,
                    // одинакова что в gas_cisterna.geo.json, что в _on-варианте). GasCisternaRenderer
                    // перед отрисовкой применяет ровно такую цепочку: translate(0.5,0,0.5) ->
                    // rotateY(-facing.toYRot()) -> scale(0.5) -> translate(-0.5,0,-0.5) — повторяем
                    // её же, чтобы игрок встал ровно там, где на модель выходит "морда"/патрубок.
                    Direction facing = state.getValue(FACING);
                    double boneLocalX = 10.0 / 16.0;
                    double boneLocalZ = 4.0 / 16.0;
                    // scale(0.5) в рендере применяется ВОКРУГ блок-центра (0.5,0.5) — поэтому
                    // сначала центрируем координату кости, потом уменьшаем вдвое.
                    double localDx = (boneLocalX - 0.5) * 0.5;
                    double localDz = (boneLocalZ - 0.5) * 0.5;
                    // Поворот вокруг Y на -facing.toYRot(), как в рендере.
                    double rad = Math.toRadians(-facing.toYRot());
                    double rotatedDx = localDx * Math.cos(rad) - localDz * Math.sin(rad);
                    double rotatedDz = localDx * Math.sin(rad) + localDz * Math.cos(rad);
                    // Отступаем от кости ещё на ~0.6 блока в направлении FACING, чтобы игрок
                    // стоял ПЕРЕД патрубком, а не внутри модели.
                    double px = pos.getX() + 0.5 + rotatedDx + facing.getStepX() * 0.9;
                    double pz = pos.getZ() + 0.5 + rotatedDz + facing.getStepZ() * 0.9;
                    float yaw = facing.getOpposite().toYRot();

                    ((net.minecraft.server.level.ServerPlayer) player).teleportTo((net.minecraft.server.level.ServerLevel) level, px, (double) pos.getY(), pz, yaw, 0.0F);

                    // Определяем анимацию и её длительность в тиках
                    String animName = isApg ? "apg_reload" : "odm_reload";
                    int lockTicks = isApg ? 59 : 52; // 2.95s и 2.6s соответственно

                    // Запускаем РАЗОВУЮ анимацию на клиенте — не TraumaAnimationPayload
                    // (тот зациклен намертво, а перезарядка — одноразовое действие,
                    // и не остановилась бы сама после конца анимации).
                    com.maxonaddon.network.GasReloadAnimPayload payload = new com.maxonaddon.network.GasReloadAnimPayload(animName, lockTicks);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send((net.minecraft.server.level.ServerPlayer) player, payload);

                    // Фиксируем игрока в системе блокировки
                    com.maxonaddon.MaxonDaotAddon.activeReloads.put(player.getUUID(), lockTicks);

                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EXTINGUISH_FIRE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
                // === ОБЫЧНОЕ ОТКРЫТИЕ ИНВЕНТАРЯ (ПКМ) ===
                else {
                    // Запас в баке теперь виден прямо в своём меню (GasCisternaScreen —
                    // полоса заполненности вместо унаследованного меню обычной печки),
                    // так что отдельное чат-сообщение об этом уже не нужно.
                    player.openMenu(cisterna);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}