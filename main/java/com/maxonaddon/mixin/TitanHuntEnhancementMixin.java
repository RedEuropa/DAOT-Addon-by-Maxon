package com.maxonaddon.mixin;

import com.maxonaddon.MaxonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;


/**
 * Делает обычных титанов опаснее для летающего на УПМ игрока.
 *
 * Не трогает шифтеров и огра — они и так сложные (по ТЗ). Работает через рефлексию
 * по тем же публичным методам daot.TitanEntity (setEatingTargetId/setEating/setRunAnimation),
 * что уже используются в PureTitanEatMixin — targeting Mob.class, чтобы не зависеть
 * от сигнатур конкретных классов базового мода.
 *
 * Лёгкий нерф (было слишком тяжело сражаться) + масштабирование от игровой сложности
 * Minecraft: на PEACEFUL система полностью выключена, на EASY заметно слабее,
 * на HARD — заметно опаснее, NORMAL — базовые (уже подрезанные) значения.
 */
@Mixin(Mob.class)
public abstract class TitanHuntEnhancementMixin {

    // БАЗОВЫЕ ЗНАЧЕНИЯ (сложность NORMAL). Раньше GRAB_REACH=5.5 использовался как
    // полу-длина стороны куба захвата — это давало куб 11х11х11 блоков вокруг головы,
    // из-за чего хватало ощутимо дальше, чем выглядело на глаз. Подрезано до 4.0
    // (куб 8х8х8) — всё ещё щедро для "рядом с лицом", но уже не через полкомнаты.
    @Unique private static final double GRAB_REACH = 4.0;           // половина стороны куба захвата
    @Unique private static final double GRAB_MAX_SPEED = 0.85;      // быстрее этого — не поймает
    @Unique private static final double PREDICT_SECONDS = 0.6;      // насколько вперёд экстраполируем
    @Unique private static final float EXTRA_TURN_FACTOR = 0.15F;   // было 0.2 — чуть придержали доворот

    // === ОКНО НА РЕАКЦИЮ ПЕРЕД ЗАХВАТОМ ===
    // Раньше захват срабатывал в тот же тик, как игрок оказывался в зоне на низкой скорости —
    // ни секунды на исправление ошибки. Теперь нужно провисеть в зоне GRAB_WARNING_TICKS тиков
    // ПОДРЯД — любое ускорение или вылет из зоны сбрасывает отсчёт. Дальше видит только сам
    // мод/сервер (per-instance @Unique — свой счётчик на каждого титана).
    @Unique private static final int GRAB_WARNING_TICKS = 20; // 1 секунда на реакцию
    @Unique private java.util.UUID maxon$grabWarnTarget;
    @Unique private int maxon$grabWarnTicks;

    // === СТОРОЖ ЗАСТРЯВШЕГО ЗАХВАТА ===
    // Если поедание начали МЫ (захват зависшего у лица игрока), а титан по какой-то причине
    // не доедает и не сбрасывает isEating() сам (жертва сбежала/умерла/отключилась в момент,
    // не предусмотренный родной логикой daot для ВОЗДУШНОГО захвата — обычно она рассчитана
    // на захват стоящих на земле) — он застревает в состоянии "ем" НАВСЕГДА и перестаёт
    // атаковать вообще, что выглядит как "титанам полностью пофиг на игрока". Даём жёсткий
    // потолок в 15 секунд на любой наш захват, дальше принудительно отменяем.
    @Unique private static final int MAX_EATING_TICKS = 300; // 15 секунд с запасом
    @Unique private boolean maxon$weStartedEating = false;
    @Unique private int maxon$eatingWatchdogTicks = 0;

    /** Множитель агрессии от текущей сложности Minecraft (не путать с нашими конфиг-шансами). */
    @Unique
    private float maxon$difficultyScale(Mob self) {
        Difficulty diff = self.level().getDifficulty();
        return switch (diff) {
            case PEACEFUL -> 0.0F; // обрабатывается отдельной ранней проверкой, сюда не доходит
            case EASY -> 0.7F;
            case NORMAL -> 1.0F;
            case HARD -> 1.3F;
        };
    }

    @Unique
    private boolean maxon$isHuntingTitan(Mob self) {
        if (self instanceof daot.ShifterTitan) return false;
        String name = self.getClass().getName();
        if (!name.startsWith("daot.")) return false;
        if (name.contains("Ogre")) return false;
        if (!name.contains("Titan") && !name.contains("ConnieFather")) return false;
        return !name.contains("Eye") && !name.contains("Nape")
                && !name.contains("Grab") && !name.contains("Dummy");
    }

    /** Аномалы (обычный и ползучий) — по лору непредсказуемые и цепкие, предугадывают всегда. */
    @Unique
    private boolean maxon$isAbnormal(Mob self) {
        return self.getClass().getName().contains("Abnormal");
    }

    /**
     * Запускает захват так, как это делает сам мод.
     *
     * ИСТОРИЯ ОШИБКИ: раньше сюда писался только eatingTarget рефлексией + setEating(true).
     * Этого было мало — по байткоду выяснилось, что у titan-семейства есть НАСТОЯЩИЙ публичный
     * (иногда приватный) метод запуска захвата, который АТОМАРНО сбрасывает ВСЕ сопутствующие
     * поля разом (eatTicks=0, grabPhaseComplete=false, eatingTargetId, у части типов ещё и
     * лаунж-рывок). Прямая запись только eatingTarget оставляла эти поля в СТАРОМ состоянии
     * от прошлого захвата — из-за этого:
     *   - у семейства FritzTitanEntity (бородач/тропический/аномал/ползучий аномал) с их
     *     фазовой системой (EatPhase/missedGrab) титан считал захват "промазанным" и уходил
     *     топтать вместо поедания;
     *   - у SmallTitan/SmallTitan2/YellowTitan titан считал, что фаза притягивания УЖЕ прошла
     *     (grabPhaseComplete оставался true с прошлого раза) и сразу ел — не давая времени
     *     ни игроку среагировать, ни нашему PureTitanEatMixin запустить QTE.
     *
     * Поэтому теперь ищем и вызываем НАСТОЯЩИЙ метод, в порядке приоритета:
     *   1) performMidAirEat(LivingEntity)         — семейство FritzTitanEntity, для захвата в воздухе
     *   2) performEat(LivingEntity, boolean)       — Small/SmallTitan2/YellowTitan (bool = рывок-лаунж)
     *   3) performEat(LivingEntity)                — TitanEntity/SadTitan/ConnieFather
     *   4) startEating(LivingEntity) (приватный)   — CrawlerTitanEntity, у него нет публичного входа
     * Прямая запись поля — самый последний фолбэк, если у класса вообще ничего из этого нет.
     */
    @Unique
    private boolean maxon$beginEating(Mob self, Player victim) {
        try {
            java.lang.reflect.Method midAir = self.getClass().getMethod("performMidAirEat", net.minecraft.world.entity.LivingEntity.class);
            midAir.invoke(self, victim);
            return true;
        } catch (NoSuchMethodException ignored) {
            // у этого титана нет отдельного метода для воздуха — пробуем дальше
        } catch (Exception e) {
            return false;
        }

        try {
            java.lang.reflect.Method lunge = self.getClass().getMethod("performEat", net.minecraft.world.entity.LivingEntity.class, boolean.class);
            lunge.invoke(self, victim, true);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return false;
        }

        try {
            java.lang.reflect.Method plain = self.getClass().getMethod("performEat", net.minecraft.world.entity.LivingEntity.class);
            plain.invoke(self, victim);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return false;
        }

        for (Class<?> cls = self.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Method startEating = cls.getDeclaredMethod("startEating", net.minecraft.world.entity.LivingEntity.class);
                startEating.setAccessible(true);
                startEating.invoke(self, victim);
                return true;
            } catch (NoSuchMethodException ignored) {
                // ищем выше по иерархии
            } catch (Exception e) {
                return false;
            }
        }

        // Последний фолбэк — старый способ (лучше кривой захват, чем никакой).
        for (Class<?> cls = self.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Field field = cls.getDeclaredField("eatingTarget");
                field.setAccessible(true);
                field.set(self, victim);

                self.getClass().getMethod("setEatingTargetId", int.class).invoke(self, victim.getId());
                self.getClass().getMethod("setEating", boolean.class).invoke(self, true);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Unique
    private boolean maxon$isEating(Mob self) {
        if (self instanceof daot.GrabbingTitan grabbing) {
            return grabbing.isEating();
        }
        return false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$enhanceTitanHunt(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) return;
        if (!MaxonConfig.get().enhancedTitanHunting) return;
        if (!maxon$isHuntingTitan(self)) return;
        if (!self.isAlive()) return;

        // Сторож застрявшего захвата — проверяем ДО раннего выхода по isEating(), иначе
        // сам сторож никогда бы не сработал, пока титан "ест".
        if (this.maxon$weStartedEating) {
            if (!maxon$isEating(self)) {
                // daot сам всё завершил штатно — просто сбрасываем наши метки.
                this.maxon$weStartedEating = false;
                this.maxon$eatingWatchdogTicks = 0;
            } else if (++this.maxon$eatingWatchdogTicks > MAX_EATING_TICKS) {
                maxon$forceCancelEating(self);
                this.maxon$weStartedEating = false;
                this.maxon$eatingWatchdogTicks = 0;
            }
        }

        if (maxon$isEating(self)) return;
        // На PEACEFUL всё усложнение полностью выключено.
        if (self.level().getDifficulty() == Difficulty.PEACEFUL) return;

        float diffScale = maxon$difficultyScale(self);
        boolean redEyed = self.getTags().contains("RedEyedTitan");

        // === 3. КРАСНОГЛАЗЫЕ: ВСЕ БЕГУТ, А НЕ ТОЛЬКО НЕКОТОРЫЕ ===
        if (redEyed && self.tickCount % 20 == 0) {
            try {
                Method setRun = self.getClass().getMethod("setRunAnimation", int.class);
                setRun.invoke(self, 1);
            } catch (Exception ignored) {
                // У этого типа титана нет анимации бега — не страшно.
            }
        }

        // === 2. ЧУТЬ БЫСТРЕЕ РАЗВОРОТ (+15% сверх штатного на NORMAL, масштабируется сложностью) ===
        // Базовую MAX_TURN_SPEED в daot трогать нельзя (private static final, заинлайнена),
        // поэтому каждый тик добавляем свой небольшой доворот к цели поверх родного.
        var target = self.getTarget();
        if (target != null) {
            double dx = target.getX() - self.getX();
            double dz = target.getZ() - self.getZ();
            float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

            float turnFactor = EXTRA_TURN_FACTOR * diffScale;
            float deltaBody = Mth.degreesDifference(self.yBodyRot, wanted);
            self.yBodyRot += deltaBody * turnFactor;
            self.setYRot(self.yBodyRot);

            float deltaHead = Mth.degreesDifference(self.yHeadRot, wanted);
            self.yHeadRot += deltaHead * turnFactor;
        }

        // === 1 + 5. ЗАХВАТ ЛЕТАЮЩЕГО/ЗАВИСШЕГО ИГРОКА У ЛИЦА ===
        maxon$tryGrabNearbyFlyer(self, redEyed, diffScale);

        // === 4. ПРЕДУГАДЫВАНИЕ ТРАЕКТОРИИ ===
        // Аномалы и красноглазые предугадывают ВСЕГДА, обычные — доля зависит от сложности:
        // на EASY реже (каждый шестой), на HARD чаще (через одного), чтобы не выглядело
        // как "все титаны разом поумнели" — но чтобы сложность реально что-то меняла.
        boolean abnormal = maxon$isAbnormal(self);
        int predictEvery = self.level().getDifficulty() == Difficulty.HARD ? 2
                : self.level().getDifficulty() == Difficulty.EASY ? 6 : 3;
        boolean predicts = redEyed || abnormal || (self.getId() % predictEvery == 0);
        if (target instanceof Player && predicts && self.tickCount % 10 == 0) {
            // Аномалы читают траекторию дальше вперёд — они и в оригинале самые непредсказуемые.
            double lead = PREDICT_SECONDS * 20.0 * (abnormal ? 1.4 : 1.0);
            Vec3 predicted = target.position().add(target.getDeltaMovement().scale(lead));
            double baseChaseSpeed = redEyed ? 1.25 : (abnormal ? 1.2 : 1.05);
            double chaseSpeed = baseChaseSpeed * diffScale;
            self.getNavigation().moveTo(predicted.x, predicted.y, predicted.z, chaseSpeed);
        }

        // === 3 (доп). КРАСНОГЛАЗЫЕ ПРЫГАЮТ ЗА ЛЕТУНОМ ===
        // Прыгают реже (было 1/20, стало 1/30) и чуть слабее (0.85 -> 0.7 базово) — легкий нерф,
        // сложность добавляет частоту и высоту прыжка обратно.
        if (redEyed && self.onGround() && target instanceof Player flyer) {
            double heightGap = flyer.getY() - self.getEyeY();
            double flatDist = Math.hypot(flyer.getX() - self.getX(), flyer.getZ() - self.getZ());
            int jumpChance = Math.max(10, Math.round(30 / diffScale));
            if (heightGap > 1.0 && heightGap < 12.0 && flatDist < 12.0 && self.getRandom().nextInt(jumpChance) == 0) {
                Vec3 toTarget = new Vec3(flyer.getX() - self.getX(), 0, flyer.getZ() - self.getZ());
                if (toTarget.lengthSqr() > 1.0E-4) {
                    toTarget = toTarget.normalize().scale(0.45);
                }
                double jumpPower = 0.7 * diffScale;
                self.setDeltaMovement(self.getDeltaMovement().add(toTarget.x, jumpPower, toTarget.z));
                self.hasImpulse = true;
            }
        }
    }

    /**
     * Титан хватает игрока, который завис у его лица (не сзади/сбоку от затылка) на невысокой
     * скорости и держится там достаточно долго. Быстро проносящегося мимо игрока по-прежнему
     * не поймать, а секундного пролёта рядом теперь тоже не хватит — нужно реально зависнуть.
     */
    @Unique
    private void maxon$tryGrabNearbyFlyer(Mob self, boolean redEyed, float diffScale) {
        // Точка отсчёта — голова титана.
        double headY = self.getY() + self.getBbHeight() * 0.85;
        float headYawRad = (float) Math.toRadians(self.yHeadRot);
        double forward = self.getBbWidth() * 0.5;
        double headX = self.getX() - Math.sin(headYawRad) * forward;
        double headZ = self.getZ() + Math.cos(headYawRad) * forward;

        // Направление "куда смотрит лицо" — используем для отсечения захвата со спины.
        double faceDirX = -Math.sin(headYawRad);
        double faceDirZ = Math.cos(headYawRad);

        // Сложность двигает и радиус захвата, и порог скорости побега в одну сторону:
        // на HARD титан достаёт чуть дальше и ловит чуть более быстрого игрока,
        // на EASY — наоборот, короче руки и легче обогнать.
        double reach = GRAB_REACH * (redEyed ? 1.25 : 1.0) * diffScale;
        double maxSpeed = GRAB_MAX_SPEED * diffScale;
        AABB box = new AABB(headX - reach, headY - reach, headZ - reach,
                headX + reach, headY + reach, headZ + reach);

        Player qualifying = null;

        for (Player player : self.level().getEntitiesOfClass(Player.class, box)) {
            if (!(player instanceof ServerPlayer serverPlayer)) continue;
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            if (player.isPassenger()) continue;

            // Только летящий/висящий в воздухе — стоящего на земле ловит обычная механика мода.
            if (player.onGround()) continue;

            // Слишком быстрый игрок проскакивает — поймать нельзя.
            double speed = player.getDeltaMovement().length();
            if (speed > maxSpeed) continue;

            // ТОЛЬКО спереди/сбоку от лица — не хватаем игрока, залетевшего титану за спину.
            // dot > 0 значит "в передней полусфере" (90° в каждую сторону от направления лица).
            double toPlayerX = player.getX() - headX;
            double toPlayerZ = player.getZ() - headZ;
            double lenSqr = toPlayerX * toPlayerX + toPlayerZ * toPlayerZ;
            if (lenSqr > 1.0E-4) {
                double dot = (toPlayerX * faceDirX + toPlayerZ * faceDirZ) / Math.sqrt(lenSqr);
                if (dot < 0.0) continue; // за спиной — пропускаем
            }

            if (com.maxonaddon.HostileQteHandler.hasGrabImmunity(serverPlayer)) continue;

            qualifying = player;
            break;
        }

        if (qualifying == null) {
            // Никто не подходит — сбрасываем отсчёт (в т.ч. если игрок улетел/ускорился).
            this.maxon$grabWarnTarget = null;
            this.maxon$grabWarnTicks = 0;
            return;
        }

        java.util.UUID uuid = qualifying.getUUID();
        if (!uuid.equals(this.maxon$grabWarnTarget)) {
            this.maxon$grabWarnTarget = uuid;
            this.maxon$grabWarnTicks = 0;
        }
        this.maxon$grabWarnTicks++;

        if (this.maxon$grabWarnTicks < GRAB_WARNING_TICKS) {
            return; // ещё копим — у игрока есть время среагировать и улететь
        }

        if (maxon$beginEating(self, qualifying)) {
            self.setTarget(qualifying);
            // Помечаем: поедание начали МЫ — сторож в maxon$enhanceTitanHunt будет следить,
            // что оно реально когда-нибудь закончится, а не зависнет навсегда.
            this.maxon$weStartedEating = true;
            this.maxon$eatingWatchdogTicks = 0;
        }
        this.maxon$grabWarnTarget = null;
        this.maxon$grabWarnTicks = 0;
    }

    /** Принудительно снимает зависшее поедание — через cancelEating(), тот же путь, что QTE-успех. */
    @Unique
    private void maxon$forceCancelEating(Mob self) {
        try {
            java.lang.reflect.Method cancel = self.getClass().getMethod("cancelEating");
            cancel.invoke(self);
        } catch (Exception ignored) {
            // Нет такого метода — тогда просто снимаем цель, хоть isEating() и не сбросится идеально.
            self.setTarget(null);
        }
    }
}
