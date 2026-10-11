package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record SyncSkillsPayload(List<String> unlockedSkills) implements CustomPacketPayload {
    public static final Type<SyncSkillsPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_skills"));

    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.unlockedSkills().size());
                for (String skill : payload.unlockedSkills()) {
                    buf.writeUtf(skill);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> skills = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    skills.add(buf.readUtf());
                }
                return new SyncSkillsPayload(skills);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}