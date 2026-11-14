package wakaba.hiiro.omnimobskiller;

import net.minecraftforge.fml.common.Mod;
import wakaba.hiiro.omnimobskiller.init.ModInit;
import wakaba.hiiro.omnimobskiller.util.RetransformClasses0Killer;

@Mod(ModMain.MODID)
public class ModMain {

    public static final String MODID = "omnimobskiller";

    static {
        try {
            RetransformClasses0Killer.patchRetransform();
        } catch (Throwable ignored) {
        }
    }

    public ModMain() {
        ModInit.init();
    }

}
