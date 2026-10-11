package com.maxonaddon.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;

public class TitanSteamParticle extends TextureSheetParticle {

    protected TitanSteamParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // Делаем партикл ОГРОМНЫМ (в 20 раз больше стандартного)
        this.scale(60.0F);
        float colorTint = 0.1F + this.random.nextFloat() * 0.1F; // Будет от 0.1 до 0.2
        this.rCol = colorTint;
        this.gCol = colorTint;
        this.bCol = colorTint;

        // Время жизни партикла (в тиках)
        this.lifetime = 300 + this.random.nextInt(40);


        this.pickSprite(spriteSet);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        // Плавное исчезновение
        if (this.age >= this.lifetime - 20) {
            this.alpha -= 0.03F;
            if (this.alpha < 0) this.alpha = 0;
        }
    }

    // Фабрика для привязки к типу партикла
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet spriteSet) {
            this.sprites = spriteSet;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new TitanSteamParticle(level, x, y, z, this.sprites, xSpeed, ySpeed, zSpeed);
        }
    }
}