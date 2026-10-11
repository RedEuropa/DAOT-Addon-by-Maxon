package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record OpenStatsScreenPayload(
        int titansKilled,
        int fearResist,
        double totalHours,
        double hoursWithoutDeath,
        String bloodline,
        String shifterStatus,
        String ymirCurseTime,
        int level,
        int exp,
        int requiredExp,
        int skillPoints,
        int bloodlinePoints,
        int titanPoints,
        int questsCompleted,
        List<String> unlockedSkills, // <--- Единый список навыков вместо кучи boolean
        int repMaria,
        int repRose,
        int repSina
) implements CustomPacketPayload {

    public static final Type<OpenStatsScreenPayload> TYPE = new Type<>(MaxonDaotAddon.id("open_stats_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenStatsScreenPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.titansKilled());
                buf.writeInt(payload.fearResist());
                buf.writeDouble(payload.totalHours());
                buf.writeDouble(payload.hoursWithoutDeath());
                buf.writeUtf(payload.bloodline());
                buf.writeUtf(payload.shifterStatus());
                buf.writeUtf(payload.ymirCurseTime());

                buf.writeInt(payload.level());
                buf.writeInt(payload.exp());
                buf.writeInt(payload.requiredExp());
                buf.writeInt(payload.skillPoints());
                buf.writeInt(payload.bloodlinePoints());
                buf.writeInt(payload.titanPoints());
                buf.writeInt(payload.questsCompleted());

                // === Записываем список навыков ===
                buf.writeVarInt(payload.unlockedSkills().size());
                for (String skill : payload.unlockedSkills()) {
                    buf.writeUtf(skill);
                }

                buf.writeInt(payload.repMaria());
                buf.writeInt(payload.repRose());
                buf.writeInt(payload.repSina());
            },
            buf -> {
                int titansKilled = buf.readInt();
                int fearResist = buf.readInt();
                double totalHours = buf.readDouble();
                double hoursWithoutDeath = buf.readDouble();
                String bloodline = buf.readUtf();
                String shifterStatus = buf.readUtf();
                String ymirCurseTime = buf.readUtf();

                int level = buf.readInt();
                int exp = buf.readInt();
                int requiredExp = buf.readInt();
                int skillPoints = buf.readInt();
                int bloodlinePoints = buf.readInt();
                int titanPoints = buf.readInt();
                int questsCompleted = buf.readInt();

                // === Читаем список навыков ===
                int skillsCount = buf.readVarInt();
                List<String> unlockedSkills = new ArrayList<>(skillsCount);
                for (int i = 0; i < skillsCount; i++) {
                    unlockedSkills.add(buf.readUtf());
                }

                int repMaria = buf.readInt();
                int repRose = buf.readInt();
                int repSina = buf.readInt();

                return new OpenStatsScreenPayload(
                        titansKilled, fearResist, totalHours, hoursWithoutDeath,
                        bloodline, shifterStatus, ymirCurseTime,
                        level, exp, requiredExp, skillPoints,
                        bloodlinePoints, titanPoints, questsCompleted,
                        unlockedSkills, repMaria, repRose, repSina
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}