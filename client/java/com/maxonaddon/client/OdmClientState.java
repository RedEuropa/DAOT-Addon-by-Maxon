package com.maxonaddon.client;

/**
 * Мелкое клиентское состояние ОДМ, общее для физики и рывка.
 * <p>
 * Живёт отдельно от миксинов: метку зацепа ставит физика (OdmPhysicsMixin), а читает её
 * рывок (LocalPlayerMixin) — складывать её в один из них означало бы, что второй лезет к
 * чужим внутренностям.
 */
public final class OdmClientState {

    /** Когда крюки в последний раз тянули игрока. По нему рывок понимает, что зацеп ещё жив. */
    public static long lastHookTime = 0L;

    private OdmClientState() {}

    /** Был ли зацеп только что — окно в 150 мс, как раз чтобы пережить один-два тика. */
    public static boolean hookedRecently() {
        return System.currentTimeMillis() - lastHookTime < 150L;
    }

    public static void reset() {
        lastHookTime = 0L;
    }
}
