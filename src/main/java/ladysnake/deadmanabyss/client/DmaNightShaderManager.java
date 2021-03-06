package ladysnake.deadmanabyss.client;

import ladylib.client.shader.ShaderUtil;
import ladylib.compat.EnhancedBusSubscriber;
import ladysnake.deadmanabyss.DeadManAbyss;
import ladysnake.deadmanabyss.config.DmaConfig;
import ladysnake.deadmanabyss.api.capability.DmaEventHandler;
import ladysnake.deadmanabyss.block.BlockMutatedBush;
import ladysnake.deadmanabyss.capability.CapabilityDmaEvent;
import ladysnake.deadmanabyss.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

@EnhancedBusSubscriber(side = Side.CLIENT)
public class DmaNightShaderManager implements ISelectiveResourceReloadListener {
    public static final ResourceLocation NIGHT_SHADER = new ResourceLocation(DeadManAbyss.MOD_ID, "shaders/post/darkness.json");
    public static final ResourceLocation FANCY_NIGHT_SHADER = new ResourceLocation(DeadManAbyss.MOD_ID, "shaders/post/fancy_darkness.json");
    private ShaderGroup shader;
    private int oldDisplayWidth, oldDisplayHeight;

    // fancy shader stuff
    private Matrix4f projectionMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();
    private Frustum frustum = new Frustum();
    private Map<TileEntity, Integer> glowingTEs = new WeakHashMap<>();
    private int updateTicks = 0;
    private boolean playerHasLantern;

    @SubscribeEvent
    public void onTickClientTick(TickEvent.ClientTickEvent event) {
        if (DmaConfig.client.fancyShader && event.phase == TickEvent.Phase.START) {
            WorldClient world = Minecraft.getMinecraft().world;
            if (world != null) {
                DmaEventHandler cap = world.getCapability(CapabilityDmaEvent.CAPABILITY_DMA_EVENT, null);
                if (cap != null && cap.isEventOccuring()) {
                    cap.tick();
                    if (updateTicks++ % 40 == 0) {
                        for (TileEntity te : world.loadedTileEntityList) {
                            if (te.getBlockType() instanceof BlockMutatedBush) {
                                glowingTEs.put(te, te.getWorld().getBlockState(te.getPos()).getLightValue(te.getWorld(), te.getPos()));
                            }
                        }
                        EntityPlayerSP player = Minecraft.getMinecraft().player;
                        playerHasLantern = false;
                        if (player != null) {
                            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                                ItemStack stack = player.inventory.getStackInSlot(i);
                                if (stack.getItem() == ModItems.ICHOR_SAC || stack.getItem() == ModItems.LIGHTBLEB) {
                                    playerHasLantern = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies the darkness shader after the world has been fully rendered
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        DmaEventHandler cap = Minecraft.getMinecraft().world.getCapability(CapabilityDmaEvent.CAPABILITY_DMA_EVENT, null);
        if (cap != null && cap.isEventOccuring()) {
            if (this.shader != null) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.displayWidth != oldDisplayWidth || mc.displayHeight != oldDisplayHeight) {
                    for (Shader shader : this.shader.listShaders) {
                        shader.getShaderManager().addSamplerTexture("DepthSampler", ShaderUtil.getDepthTexture());
                        shader.getShaderManager().getShaderUniformOrDefault("ViewPort").set(0, 0, mc.displayWidth, mc.displayHeight);
                    }
                    this.shader.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                    oldDisplayWidth = mc.displayWidth;
                    oldDisplayHeight = mc.displayHeight;
                }
                if (DmaConfig.client.fancyShader) {
                    for (Shader shader : this.shader.listShaders) {
                        shader.getShaderManager().getShaderUniformOrDefault("Progress").set(1f);
                    }
                    setupFancyUniforms(mc);
                }
                GlStateManager.matrixMode(GL11.GL_TEXTURE);
                GlStateManager.loadIdentity();
                shader.render(event.getPartialTicks());
                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
                GlStateManager.disableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); // restore blending
                GlStateManager.enableDepth();
            } else if (OpenGlHelper.areShadersSupported()) {
                ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(this);
            }
        }
    }

    private void setupFancyUniforms(Minecraft mc) {
        ShaderManager sm = this.shader.listShaders.get(0).getShaderManager();
        ShaderUtil.useShader(sm.getProgram());
        Entity camera = mc.getRenderViewEntity();
        frustum.setPosition(camera.posX, camera.posY, camera.posZ);
        ShaderUtil.setUniform("InverseTransformMatrix", computeInverseTransformMatrix());
        int lightCount = 0;
        // Add a visibility area around the player
        ShaderUtil.setUniform("Lights[" + lightCount + "].position", 0f, 0f, 0f);
        ShaderUtil.setUniform("Lights[" + lightCount + "].radius", 40f);
        lightCount++;
        for (Iterator<Map.Entry<TileEntity, Integer>> iterator = glowingTEs.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<TileEntity, Integer> entry = iterator.next();
            TileEntity te = entry.getKey();
            float lightValue = entry.getValue();
            if (te.isInvalid()) {
                iterator.remove();
            } else {
                // TODO frustum check
                BlockPos pos = te.getPos();
                float posX = pos.getX() + 0.5f;
                float posY = pos.getY() + 0.5f;
                float posZ = pos.getZ() + 0.5f;
                if (frustum.isBoxInFrustum(posX - lightValue, posY - lightValue, posZ - lightValue, posX + lightValue, posY + lightValue, posZ + lightValue)) {
                    float x = (float) (posX - Particle.interpPosX);
                    float y = (float) (posY - Particle.interpPosY);
                    float z = (float) (posZ - Particle.interpPosZ);
                    ShaderUtil.setUniform("Lights[" + lightCount + "].position", x, y, z);
                    ShaderUtil.setUniform("Lights[" + lightCount + "].radius", lightValue);
                    lightCount++;
                }
            }
        }
        ShaderUtil.setUniform("LightCount", lightCount);
        ShaderUtil.revert();
    }

    /**
     * @return the matrix allowing computation of eye space coordinates from window space
     */
    @Nonnull
    private FloatBuffer computeInverseTransformMatrix() {
        projectionMatrix.load(ShaderUtil.getProjectionMatrix());

        viewMatrix.load(ShaderUtil.getModelViewMatrix());

        Matrix4f projectionViewMatrix = Matrix4f.mul(projectionMatrix, viewMatrix, null);
        // reuse the projection matrix instead of creating a new one
        Matrix4f inverseTransformMatrix = Matrix4f.invert(projectionViewMatrix, projectionMatrix);

        FloatBuffer buf = ShaderUtil.getTempBuffer();
        inverseTransformMatrix.store(buf);
        buf.rewind();
        return buf;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate) {
        if (resourcePredicate.test(VanillaResourceType.SHADERS)) {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                this.shader = new ShaderGroup(
                        mc.renderEngine,
                        resourceManager,
                        mc.getFramebuffer(),
                        DmaConfig.client.fancyShader ? FANCY_NIGHT_SHADER : NIGHT_SHADER
                );
                this.oldDisplayWidth = -1; // reset framebuffers next tick
            } catch (IOException e) {
                DeadManAbyss.LOGGER.error("Could not load darkness shader", e);
            }
        }
    }
}
