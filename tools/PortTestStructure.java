package thaumicenergistics_ce;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PortTestStructure {
    public static void main(String[] args) throws Exception {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 3955);
        var size = new ListTag();
        for (int i = 0; i < 3; i++) size.add(IntTag.valueOf(1));
        tag.put("size", size);
        tag.put("blocks", new ListTag());
        tag.put("entities", new ListTag());
        var palette = new ListTag();
        var air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        tag.put("palette", palette);
        var output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(tag, output);
    }
}
