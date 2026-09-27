import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Shared, lossless access to named Blockbench source data. */
public final class BlockbenchSource {
    private BlockbenchSource() { }

    public static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    public static JsonObject named(JsonArray entries, String name) {
        JsonObject found = null;
        for (var entry : entries) {
            JsonObject object = entry.getAsJsonObject();
            if (object.get("name").getAsString().equals(name)) {
                if (found != null) throw new IllegalArgumentException("Duplicate Blockbench name: " + name);
                found = object;
            }
        }
        if (found == null) throw new IllegalArgumentException("Missing Blockbench entry: " + name);
        return found;
    }

    public static byte[] png(JsonObject texture) throws IOException {
        String source = texture.get("source").getAsString();
        String prefix = "data:image/png;base64,";
        if (!source.startsWith(prefix)) throw new IOException("Expected embedded PNG: " + texture.get("name"));
        byte[] bytes = Base64.getDecoder().decode(source.substring(prefix.length()));
        if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 'P' || bytes[2] != 'N'
                || bytes[3] != 'G' || bytes[4] != 13 || bytes[5] != 10 || bytes[6] != 26 || bytes[7] != 10)
            throw new IOException("Invalid PNG signature: " + texture.get("name"));
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() != texture.get("width").getAsInt()
                || image.getHeight() != texture.get("height").getAsInt())
            throw new IOException("PNG dimensions do not match Blockbench texture: " + texture.get("name"));
        return bytes;
    }
}
