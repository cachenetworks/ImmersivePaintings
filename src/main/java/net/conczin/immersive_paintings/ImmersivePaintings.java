package net.conczin.immersive_paintings;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = ImmersivePaintings.MODID, name = "Immersive Paintings 1.12.2",
     version = "0.1.0-alpha", acceptedMinecraftVersions = "[1.12.2]",
     dependencies = "required-after:forge@[14.23.5.2859,)")
@Mod.EventBusSubscriber(modid = ImmersivePaintings.MODID)
public class ImmersivePaintings {
    public static final String MODID = "immersive_paintings";
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
    public static final ItemCustomPainting PAINTING_ITEM = (ItemCustomPainting)
        new ItemCustomPainting().setRegistryName(MODID, "immersive_painting")
            .setUnlocalizedName("immersive_painting")
            .setCreativeTab(CreativeTabs.DECORATIONS);

    @Mod.Instance(MODID)
    public static ImmersivePaintings INSTANCE;

    @SidedProxy(clientSide = "net.conczin.immersive_paintings.ClientProxy",
                serverSide = "net.conczin.immersive_paintings.CommonProxy")
    public static CommonProxy PROXY;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "painting"),
            EntityCustomPainting.class, "immersive_painting", 0, this, 160, 20, false);
        NETWORK.registerMessage(MessageUpload.Handler.class, MessageUpload.class, 0, Side.SERVER);
        PROXY.preInit();
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(PAINTING_ITEM);
    }
}
