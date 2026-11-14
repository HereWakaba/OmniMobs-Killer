package wakaba.hiiro.omnimobskiller.util;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;


public final class RetransformClasses0Killer {

    private static final String RETRANSFORM =
            "Java_sun_instrument_InstrumentationImpl_retransformClasses0";

    private static final byte[] STUB = {(byte) 0xC3};

    private static final String DLL_NAME = "instrument.dll";
    private static final int PAGE_EXECUTE_READWRITE = 0x40;
    private static final int GET_MODULE_HANDLE_EX_FLAG_PIN = 0x1;
    private static final int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT = 0x2;

    private static final int DOS_OFF_LFANEW = 0x3C;
    private static final int NT_OFF_SIGNATURE = 0;
    private static final int NT_OFF_MACHINE = 4;
    private static final int NT_OFF_OPTIONAL = 24;
    private static final int OPT_OFF_MAGIC = 0;
    private static final int OPT_OFF_SIZE_OF_IMAGE = 56;
    private static final int OPT_OFF_DIR_COUNT = 108;
    private static final int OPT_OFF_DATA_DIRECTORIES = 112;
    private static final int EXPORT_OFF_NUM_FUNCTIONS = 20;
    private static final int EXPORT_OFF_NUM_NAMES = 24;
    private static final int EXPORT_OFF_ADDRESS_OF_FUNCTIONS = 28;
    private static final int EXPORT_OFF_ADDRESS_OF_NAMES = 32;
    private static final int EXPORT_OFF_ADDRESS_OF_NAME_ORDINALS = 36;
    private static final int PE32_MAGIC = 0x20b;
    private static final int MACHINE_AMD64 = 0x8664;
    private static final int PE_SIGNATURE = 0x00004550;

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE =
                Native.load("kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);

        boolean GetModuleHandleExW(int dwFlags, String moduleName, Pointer[] hModule);

        Pointer LoadLibraryExW(String filename, Pointer hFile, int dwFlags);

        boolean VirtualProtect(Pointer address, NativeLong size, int newProtect,
                IntByReference oldProtect);

        boolean FlushInstructionCache(Pointer hProcess, Pointer baseAddress, NativeLong size);

        Pointer GetCurrentProcess();
    }

    private static final MethodHandle GET_BYTE;
    private static final MethodHandle PUT_BYTE;
    private static final boolean UNSAFE_READY;

    static {
        MethodHandle get = null;
        MethodHandle put = null;
        boolean ready = false;
        try {
            Class<?> cls = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = cls.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            get = lookup.findVirtual(cls, "getByte",
                    MethodType.methodType(byte.class, long.class)).bindTo(unsafe);
            put = lookup.findVirtual(cls, "putByte",
                    MethodType.methodType(void.class, long.class, byte.class)).bindTo(unsafe);
            ready = true;
        } catch (Throwable ignored) {
        }
        GET_BYTE = get;
        PUT_BYTE = put;
        UNSAFE_READY = ready;
    }

    public static long patchRetransform() {
        try {
            if (!UNSAFE_READY || !Platform.isWindows() || !Platform.is64Bit()) {
                return 0;
            }
            String dll = dllPath();
            if (dll == null) {
                return 0;
            }
            long address = module(dll);
            if (address == 0) {
                return 0;
            }
            pin(dll);
            long target = export(address, RETRANSFORM);
            if (target == 0 || stubbed(target)) {
                return target;
            }
            write(target, STUB);
            return stubbed(target) ? target : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String dllPath() {
        String[] roots = {System.getProperty("sun.boot.library.path"),
                System.getProperty("java.home"), System.getProperty("user.dir")};
        for (String root : roots) {
            if (root == null) {
                continue;
            }
            for (Path probe : new Path[] {Paths.get(root, DLL_NAME), Paths.get(root, "bin", DLL_NAME)}) {
                if (Files.isRegularFile(probe)) {
                    return probe.toAbsolutePath().normalize().toString();
                }
            }
        }
        return null;
    }

    private static long module(String dll) {
        Pointer[] existing = new Pointer[1];
        if (Kernel32.INSTANCE.GetModuleHandleExW(
                GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, dll, existing)
                && existing[0] != null) {
            return Pointer.nativeValue(existing[0]);
        }
        Pointer handle = Kernel32.INSTANCE.LoadLibraryExW(dll, null, 0);
        return handle == null ? 0 : Pointer.nativeValue(handle);
    }

    private static void pin(String dll) {
        try {
            Pointer[] ignored = new Pointer[1];
            Kernel32.INSTANCE.GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_PIN, dll, ignored);
        } catch (Throwable ignored) {
        }
    }

    private static long export(long base, String symbol) throws Throwable {
        Pointer image = new Pointer(base);
        int lfanew = image.getInt(DOS_OFF_LFANEW);
        if (lfanew <= 0 || lfanew > 0x1000) {
            return 0;
        }
        long nt = base + lfanew;
        Pointer ntPtr = new Pointer(nt);
        if (ntPtr.getInt(NT_OFF_SIGNATURE) != PE_SIGNATURE) {
            return 0;
        }
        if ((ntPtr.getShort(NT_OFF_MACHINE) & 0xFFFF) != MACHINE_AMD64) {
            return 0;
        }
        long opt = nt + NT_OFF_OPTIONAL;
        Pointer optPtr = new Pointer(opt);
        if ((optPtr.getShort(OPT_OFF_MAGIC) & 0xFFFF) != PE32_MAGIC) {
            return 0;
        }
        int sizeOfImage = optPtr.getInt(OPT_OFF_SIZE_OF_IMAGE);
        if (sizeOfImage <= 0 || sizeOfImage > 0x10000000
                || optPtr.getInt(OPT_OFF_DIR_COUNT) <= 0) {
            return 0;
        }
        Pointer dirs = new Pointer(opt + OPT_OFF_DATA_DIRECTORIES);
        int exportRva = dirs.getInt(0);
        int exportSize = dirs.getInt(4);
        if (exportRva <= 0 || exportRva >= sizeOfImage || exportSize <= 0) {
            return 0;
        }
        Pointer dir = new Pointer(base + exportRva);
        int nameCount = dir.getInt(EXPORT_OFF_NUM_NAMES);
        int funcCount = dir.getInt(EXPORT_OFF_NUM_FUNCTIONS);
        if (nameCount <= 0 || funcCount <= 0 || nameCount > 0x100000 || funcCount > 0x100000) {
            return 0;
        }
        Pointer functions = new Pointer(base + dir.getInt(EXPORT_OFF_ADDRESS_OF_FUNCTIONS));
        Pointer names = new Pointer(base + dir.getInt(EXPORT_OFF_ADDRESS_OF_NAMES));
        Pointer ordinals = new Pointer(base + dir.getInt(EXPORT_OFF_ADDRESS_OF_NAME_ORDINALS));
        for (int i = 0; i < nameCount; i++) {
            int nameRva = names.getInt(i * 4);
            if (nameRva <= 0 || nameRva >= sizeOfImage) {
                continue;
            }
            if (!symbol.equals(new Pointer(base + nameRva).getString(0))) {
                continue;
            }
            int index = ordinals.getShort(i * 2) & 0xFFFF;
            if (index >= funcCount) {
                continue;
            }
            int rva = functions.getInt(index * 4);
            if (rva <= 0 || rva >= sizeOfImage) {
                continue;
            }
            if (rva >= exportRva && rva < exportRva + exportSize) {
                continue; // forwarded export
            }
            return base + rva;
        }
        return 0;
    }

    private static boolean stubbed(long address) throws Throwable {
        return Arrays.equals(readBytes(address, STUB.length), STUB);
    }

    private static byte[] readBytes(long address, int length) throws Throwable {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) {
            out[i] = (Byte) GET_BYTE.invoke(address + i);
        }
        return out;
    }

    private static void write(long address, byte[] data) throws Throwable {
        IntByReference old = new IntByReference();
        if (!Kernel32.INSTANCE.VirtualProtect(new Pointer(address), new NativeLong(data.length),
                PAGE_EXECUTE_READWRITE, old)) {
            return;
        }
        try {
            for (int i = 0; i < data.length; i++) {
                PUT_BYTE.invoke(address + i, data[i]);
            }
        } finally {
            Kernel32.INSTANCE.VirtualProtect(new Pointer(address), new NativeLong(data.length),
                    old.getValue(), new IntByReference());
            Kernel32.INSTANCE.FlushInstructionCache(Kernel32.INSTANCE.GetCurrentProcess(),
                    new Pointer(address), new NativeLong(data.length));
        }
    }

    private RetransformClasses0Killer() {
    }
}
