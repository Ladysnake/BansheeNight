package ladysnake.bansheenight;

import ladylib.LadyLib;
import ladylib.misc.TemplateUtil;
import ladysnake.bansheenight.command.CommandBansheeNight;
import ladysnake.bansheenight.compat.CompatMobSpawn;
import ladysnake.bansheenight.init.ModEntities;
import ladysnake.bansheenight.network.PacketHandler;
import ladysnake.bansheenight.tileentity.TileEntityBlockHolder;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = BansheeNight.MOD_ID,
        name = BansheeNight.MOD_NAME,
        version = BansheeNight.VERSION,
        certificateFingerprint = "@FINGERPRINTKEY@",
        dependencies = "required-after:forge;" +
                       "required-after:ladylib;" +
                       "after:jei"
)
public class BansheeNight {

    public static final String MOD_ID = "bansheenight";
    public static final String MOD_NAME = "Banshee Night";
    public static final String VERSION = "@VERSION@";

    public static final BansheeNight INSTANCE = new BansheeNight();
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    @Mod.InstanceFactory
    public static BansheeNight instance() {
        return INSTANCE;
    }

    /**
     * This is the first initialization event. Register tile entities here.
     * The registry events below will have fired prior to entry to this method.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if(event.getSide().isClient()) {
            ModEntities.registerRenders();
        }
    }

    /**
     * This is the second initialization event. Register custom recipes
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        PacketHandler.initPackets();
        GameRegistry.registerTileEntity(TileEntityBlockHolder.class, new ResourceLocation(BansheeNight.MOD_ID, "block_holder"));
        TemplateUtil.generateStubBlockstates(LadyLib.INSTANCE.getBlockRegistrar(), null);
        TemplateUtil.generateStubModels(MOD_ID, null);
    }


    /**
     * This is the final initialization event. Register actions from other mods here
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CompatMobSpawn.init();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBansheeNight());
    }

}
