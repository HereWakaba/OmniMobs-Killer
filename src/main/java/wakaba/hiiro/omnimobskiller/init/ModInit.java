package wakaba.hiiro.omnimobskiller.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import wakaba.hiiro.omnimobskiller.item.OmnimobsKillerItem;

@SuppressWarnings("removal")
public class ModInit {

    private static final DeferredRegister<Item> ITEM_REGISTER =
            DeferredRegister.create
                    (net.minecraftforge.registries.ForgeRegistries.ITEMS, "omnimobskiller");

    private static final DeferredRegister<CreativeModeTab> TAB_REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "omnimobskiller");

    private static final RegistryObject<Item> OMNIMOBSKILLER = ITEM_REGISTER.register("omnimobskiller",
            () -> new OmnimobsKillerItem(new Item.Properties()));

    private static final RegistryObject<CreativeModeTab> KILLER_TAB =
            TAB_REGISTER.register("killer_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("Killer Tab"))
                    .icon(() -> new ItemStack(OMNIMOBSKILLER.get()))
                    .displayItems((params, output) -> {
                        output.accept(OMNIMOBSKILLER.get());
                    })
                    .build());

    public static void init() {
        ITEM_REGISTER.register(FMLJavaModLoadingContext.get().getModEventBus());
        TAB_REGISTER.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

}
