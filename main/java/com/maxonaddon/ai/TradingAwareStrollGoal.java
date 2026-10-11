package com.maxonaddon.ai;

import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.Villager;

/**
 * Обычное ванильное блуждание (WaterAvoidingRandomStrollGoal), но с оглядкой на торговлю.
 *
 * Баг: мы добавляем эту цель КАЖДОМУ жителю в customServerAiStep без проверки isTrading().
 * Пока игрок стоит в открытом меню покупки, эта цель как ни в чём не бывало могла в любой
 * момент решить пойти погулять — житель сдвигался с места, и MerchantMenu#stillValid()
 * закрывал меню мгновенно (ровно то, что "он даже не убегает, просто меню закрывается").
 * Ванильные Brain-задачи такое явно учитывают, а наша сырая GoalSelector-цель — нет,
 * поэтому оборачиваем сами.
 */
public class TradingAwareStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final Villager villager;

    public TradingAwareStrollGoal(Villager villager, double speedModifier) {
        super(villager, speedModifier);
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return !villager.isTrading() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !villager.isTrading() && super.canContinueToUse();
    }
}
