package ans.bedrock;

import ans.molang.MolangExpr;
import ans.molang.MolangMath;
import ans.molang.MolangScope;
import ans.molang.MolangValue;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime simulator and camera-facing billboard renderer for Bedrock particles
 * spawned by Actions &amp; Stuff animations and controllers.
 */
public final class BedrockParticleEngine {

    private static final int MAX_PARTICLES = 2048;
    private final List<ActiveEmitter> emitters = new ArrayList<>();
    private final List<LiveParticle> particles = new ArrayList<>();
    private final Map<Identifier, Boolean> validTextures = new ConcurrentHashMap<>();

    public static final class ActiveEmitter {
        public final BedrockParticleDef def;
        public final MolangScope scope;
        public float x, y, z;
        public float age = 0f;
        public float lifetime;
        public float accumulator = 0f;
        public boolean spawnedInstant = false;

        public ActiveEmitter(BedrockParticleDef def, MolangScope ownerScope, float x, float y, float z) {
            this.def = def;
            this.scope = ownerScope != null ? ownerScope.pushTempScope() : new MolangScope(null);
            this.x = x;
            this.y = y;
            this.z = z;
            for (MolangExpr init : def.emitterInit) init.evaluate(scope);
            this.lifetime = Math.max(0.05f, def.activeTime.evaluateFloat(scope));
        }
    }

    public static final class LiveParticle {
        public final BedrockParticleDef def;
        public final MolangScope scope;
        public float x, y, z;
        public float vx, vy, vz;
        public float rotDeg, rotVelDeg;
        public float age = 0f;
        public float maxLifetime;
        public final float rand1, rand2, rand3, rand4;

        public LiveParticle(BedrockParticleDef def, MolangScope emitterScope, float x, float y, float z,
                            float vx, float vy, float vz, float rotDeg, float rotVelDeg, float maxLifetime) {
            this.def = def;
            this.scope = emitterScope.pushTempScope();
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.rotDeg = rotDeg;
            this.rotVelDeg = rotVelDeg;
            this.maxLifetime = Math.max(0.05f, maxLifetime);
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            this.rand1 = rng.nextFloat();
            this.rand2 = rng.nextFloat();
            this.rand3 = rng.nextFloat();
            this.rand4 = rng.nextFloat();
            updateScopeVars();
        }

        public void updateScopeVars() {
            scope.setVariable("particle_age", MolangValue.of(age));
            scope.setVariable("particle_lifetime", MolangValue.of(maxLifetime));
            scope.setVariable("particle_random_1", MolangValue.of(rand1));
            scope.setVariable("particle_random_2", MolangValue.of(rand2));
            scope.setVariable("particle_random_3", MolangValue.of(rand3));
            scope.setVariable("particle_random_4", MolangValue.of(rand4));
        }
    }

    public void spawnEmitter(BedrockParticleDef def, MolangScope ownerScope, float x, float y, float z) {
        if (def == null) return;
        ActiveEmitter em = new ActiveEmitter(def, ownerScope, x, y, z);
        if (def.rateMode == BedrockParticleDef.EmitterRateMode.INSTANT || def.rateMode == BedrockParticleDef.EmitterRateMode.MANUAL) {
            int count = (int) Math.min(64, Math.max(1, def.maxParticles.evaluateFloat(em.scope)));
            for (int i = 0; i < count; i++) {
                emitOne(em);
            }
            em.spawnedInstant = true;
        } else {
            emitters.add(em);
        }
    }

    public void tick(float dt) {
        Iterator<ActiveEmitter> eIt = emitters.iterator();
        while (eIt.hasNext()) {
            ActiveEmitter em = eIt.next();
            em.age += dt;
            em.scope.setVariable("emitter_age", MolangValue.of(em.age));
            em.scope.setVariable("emitter_lifetime", MolangValue.of(em.lifetime));
            em.def.evalCurves(em.scope);
            for (MolangExpr upd : em.def.emitterUpdate) upd.evaluate(em.scope);

            boolean expired = em.def.lifetimeMode == BedrockParticleDef.EmitterLifetimeMode.ONCE && em.age >= em.lifetime;
            if (em.def.lifetimeMode == BedrockParticleDef.EmitterLifetimeMode.EXPRESSION && em.def.expirationExpr.evaluateBoolean(em.scope)) {
                expired = true;
            }
            if (!expired) {
                float rate = Math.max(0f, em.def.spawnRate.evaluateFloat(em.scope));
                em.accumulator += rate * dt;
                int toSpawn = (int) Math.min(16, Math.floor(em.accumulator));
                em.accumulator -= toSpawn;
                for (int i = 0; i < toSpawn; i++) {
                    emitOne(em);
                }
            } else {
                eIt.remove();
            }
        }

        Iterator<LiveParticle> pIt = particles.iterator();
        while (pIt.hasNext()) {
            LiveParticle p = pIt.next();
            p.age += dt;
            p.updateScopeVars();
            p.def.evalCurves(p.scope);
            for (MolangExpr upd : p.def.particleUpdate) upd.evaluate(p.scope);

            if (p.age >= p.maxLifetime || p.def.particleExpiration.evaluateBoolean(p.scope)) {
                pIt.remove();
                continue;
            }

            if (p.def.parametricMotion) {
                if (p.def.parametricPos != null) {
                    p.x += p.def.parametricPos[0].evaluateFloat(p.scope) * dt;
                    p.y += p.def.parametricPos[1].evaluateFloat(p.scope) * dt;
                    p.z += p.def.parametricPos[2].evaluateFloat(p.scope) * dt;
                }
                if (p.def.parametricRot != null) {
                    p.rotDeg = p.def.parametricRot.evaluateFloat(p.scope);
                }
            } else {
                float ax = p.def.linearAccel[0].evaluateFloat(p.scope);
                float ay = p.def.linearAccel[1].evaluateFloat(p.scope);
                float az = p.def.linearAccel[2].evaluateFloat(p.scope);
                float drag = Math.max(0f, p.def.linearDrag.evaluateFloat(p.scope));
                p.vx += (ax - p.vx * drag) * dt;
                p.vy += (ay - p.vy * drag) * dt;
                p.vz += (az - p.vz * drag) * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.z += p.vz * dt;
                p.rotVelDeg += p.def.rotAccel.evaluateFloat(p.scope) * dt;
                p.rotDeg += p.rotVelDeg * dt;
            }
        }
    }

    private void emitOne(ActiveEmitter em) {
        if (particles.size() >= MAX_PARTICLES) return;
        BedrockParticleDef d = em.def;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        em.scope.setVariable("emitter_random_1", MolangValue.of(rng.nextFloat()));
        em.scope.setVariable("emitter_random_2", MolangValue.of(rng.nextFloat()));
        em.scope.setVariable("emitter_random_3", MolangValue.of(rng.nextFloat()));
        em.scope.setVariable("emitter_random_4", MolangValue.of(rng.nextFloat()));

        float ox = -d.shapeOffset[0].evaluateFloat(em.scope);
        float oy = d.shapeOffset[1].evaluateFloat(em.scope);
        float oz = d.shapeOffset[2].evaluateFloat(em.scope);
        float dx = 0f, dy = 0f, dz = 0f;

        if (d.shape == BedrockParticleDef.EmitterShape.SPHERE) {
            float rad = d.shapeRadius.evaluateFloat(em.scope) * (float) Math.cbrt(rng.nextDouble());
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double cosPhi = rng.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            dx = (float) (sinPhi * Math.cos(theta));
            dy = (float) cosPhi;
            dz = (float) (sinPhi * Math.sin(theta));
            ox += dx * rad;
            oy += dy * rad;
            oz += dz * rad;
        } else if (d.shape == BedrockParticleDef.EmitterShape.BOX) {
            float hx = d.shapeHalfDims[0].evaluateFloat(em.scope);
            float hy = d.shapeHalfDims[1].evaluateFloat(em.scope);
            float hz = d.shapeHalfDims[2].evaluateFloat(em.scope);
            ox += (rng.nextFloat() * 2f - 1f) * hx;
            oy += (rng.nextFloat() * 2f - 1f) * hy;
            oz += (rng.nextFloat() * 2f - 1f) * hz;
            dy = 1f;
        } else if (d.shape == BedrockParticleDef.EmitterShape.DISC) {
            float rad = d.shapeRadius.evaluateFloat(em.scope) * (float) Math.sqrt(rng.nextDouble());
            double theta = rng.nextDouble() * Math.PI * 2.0;
            dx = (float) Math.cos(theta);
            dz = (float) Math.sin(theta);
            ox += dx * rad;
            oz += dz * rad;
        } else {
            dy = 1f;
        }

        float vx, vy, vz;
        if (d.initialSpeedVec != null) {
            vx = -d.initialSpeedVec[0].evaluateFloat(em.scope);
            vy = d.initialSpeedVec[1].evaluateFloat(em.scope);
            vz = d.initialSpeedVec[2].evaluateFloat(em.scope);
        } else {
            float spd = d.initialSpeedScalar.evaluateFloat(em.scope);
            if (d.shapeDir != null) {
                dx = -d.shapeDir[0].evaluateFloat(em.scope);
                dy = d.shapeDir[1].evaluateFloat(em.scope);
                dz = d.shapeDir[2].evaluateFloat(em.scope);
            }
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-5f) {
                dx /= len; dy /= len; dz /= len;
            }
            vx = dx * spd; vy = dy * spd; vz = dz * spd;
        }

        float life = d.particleMaxLifetime.evaluateFloat(em.scope);
        float rot = d.initialRot.evaluateFloat(em.scope);
        float rotRate = d.initialRotRate.evaluateFloat(em.scope);
        particles.add(new LiveParticle(d, em.scope, em.x + ox, em.y + oy, em.z + oz, vx, vy, vz, rot, rotRate, life));
    }

    public int getActiveParticleCount() {
        return particles.size();
    }

    public int getActiveEmitterCount() {
        return emitters.size();
    }

    public void clear() {
        emitters.clear();
        particles.clear();
        validTextures.clear();
    }

    public void render(MatrixStack matrices, Camera camera) {
        if (particles.isEmpty()) return;
        Vec3d camPos = camera.getPos();
        Quaternionf camRot = camera.getRotation();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (int i = 0; i < particles.size(); i++) {
            LiveParticle p = particles.get(i);
            BedrockParticleDef d = p.def;
            float rx = (float) (p.x - camPos.x);
            float ry = (float) (p.y - camPos.y);
            float rz = (float) (p.z - camPos.z);

            float sx = Math.max(0.01f, d.sizeX.evaluateFloat(p.scope));
            float sy = Math.max(0.01f, d.sizeY.evaluateFloat(p.scope));

            float[] uv = evalParticleUv(d, p);
            float[] col = evalParticleColor(d, p);

            if (d.materialType == BedrockParticleDef.MaterialType.ADD) {
                RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            Identifier texId = Identifier.tryParse("minecraft:" + d.texturePath + ".png");
            if (texId == null) {
                texId = new Identifier("minecraft", "textures/particle/particles.png");
            }
            MinecraftClient client = MinecraftClient.getInstance();
            final Identifier lookupId = texId;
            boolean exists = validTextures.computeIfAbsent(lookupId,
                id -> client != null && client.getResourceManager() != null && client.getResourceManager().getResource(id).isPresent());
            if (!exists) {
                continue;
            }
            RenderSystem.setShaderTexture(0, texId);

            matrices.push();
            matrices.translate(rx, ry, rz);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            Vector3f right = new Vector3f(1f, 0f, 0f).rotate(camRot);
            Vector3f up = new Vector3f(0f, 1f, 0f).rotate(camRot);
            if (Math.abs(p.rotDeg) > 0.01f) {
                Quaternionf spin = new Quaternionf().fromAxisAngleDeg(new Vector3f(0f, 0f, 1f).rotate(camRot), p.rotDeg);
                right.rotate(spin);
                up.rotate(spin);
            }
            right.mul(sx);
            up.mul(sy);

            int r = clampByte(col[0]);
            int g = clampByte(col[1]);
            int b = clampByte(col[2]);
            int a = clampByte(col[3]);

            buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buf.vertex(mat, -right.x - up.x, -right.y - up.y, -right.z - up.z).texture(uv[0], uv[3]).color(r, g, b, a).next();
            buf.vertex(mat, -right.x + up.x, -right.y + up.y, -right.z + up.z).texture(uv[0], uv[1]).color(r, g, b, a).next();
            buf.vertex(mat, right.x + up.x, right.y + up.y, right.z + up.z).texture(uv[2], uv[1]).color(r, g, b, a).next();
            buf.vertex(mat, right.x - up.x, right.y - up.y, right.z - up.z).texture(uv[2], uv[3]).color(r, g, b, a).next();
            BufferRenderer.drawWithGlobalProgram(buf.end());

            matrices.pop();
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static float[] evalParticleUv(BedrockParticleDef d, LiveParticle p) {
        if (d.hasFlipbook) {
            float bu = d.fbBaseU.evaluateFloat(p.scope);
            float bv = d.fbBaseV.evaluateFloat(p.scope);
            int maxF = Math.max(1, (int) d.fbMaxFrame.evaluateFloat(p.scope));
            int frame;
            if (d.fbStretchToLifetime) {
                frame = Math.min(maxF - 1, (int) ((p.age / p.maxLifetime) * maxF));
            } else {
                int raw = (int) (p.age * d.fbFps);
                frame = d.fbLoop ? (raw % maxF) : Math.min(maxF - 1, raw);
            }
            float u = bu + frame * d.fbStepU;
            float v = bv + frame * d.fbStepV;
            return new float[]{u / d.texWidth, v / d.texHeight, (u + d.fbSizeU) / d.texWidth, (v + d.fbSizeV) / d.texHeight};
        }
        float u = d.uvU.evaluateFloat(p.scope);
        float v = d.uvV.evaluateFloat(p.scope);
        float w = d.uvW.evaluateFloat(p.scope);
        float h = d.uvH.evaluateFloat(p.scope);
        return new float[]{u / d.texWidth, v / d.texHeight, (u + w) / d.texWidth, (v + h) / d.texHeight};
    }

    private static float[] evalParticleColor(BedrockParticleDef d, LiveParticle p) {
        if (d.colorGradient != null && !d.colorGradient.isEmpty()) {
            float t = (float) MolangMath.clamp(d.colorInterpolant.evaluateFloat(p.scope), 0.0, 1.0);
            Map.Entry<Float, float[]> fl = d.colorGradient.floorEntry(t);
            Map.Entry<Float, float[]> ce = d.colorGradient.ceilingEntry(t);
            if (fl == null) return ce.getValue();
            if (ce == null || fl.getKey().equals(ce.getKey())) return fl.getValue();
            float k = (t - fl.getKey()) / (ce.getKey() - fl.getKey());
            float[] a = fl.getValue(), b = ce.getValue();
            return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k};
        }
        return new float[]{
            d.colorTint[0].evaluateFloat(p.scope),
            d.colorTint[1].evaluateFloat(p.scope),
            d.colorTint[2].evaluateFloat(p.scope),
            d.colorTint[3].evaluateFloat(p.scope)
        };
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
