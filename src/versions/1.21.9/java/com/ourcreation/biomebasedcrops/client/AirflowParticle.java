package com.ourcreation.biomebasedcrops.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A small, fading air wisp using vanilla particle sprites. */
public final class AirflowParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    private AirflowParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        setParticleSpeed(xSpeed, ySpeed, zSpeed);
        setSize(0.05F, 0.05F);
        quadSize = 0.045F + random.nextFloat() * 0.015F;
        lifetime = 18 + random.nextInt(11);
        friction = 0.96F;
        gravity = 0;
        setColor(0.9F, 0.9F, 0.9F);
        setAlpha(0);
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        float remaining = Math.max(0, 1 - age / (float) lifetime);
        setAlpha(0.22F * Math.min(1, age / 3.0F) * remaining);
        setSpriteFromAge(sprites);
    }

    @Override public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new AirflowParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
