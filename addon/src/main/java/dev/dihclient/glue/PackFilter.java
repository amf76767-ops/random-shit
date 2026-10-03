package dev.dihclient.glue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Copies a resource pack zip and leaves out the files the player switched off. */
public final class PackFilter {
    private PackFilter() {
    }

    public static byte[] copy(InputStream zip, Predicate<String> skip) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipInputStream zin = new ZipInputStream(zip); ZipOutputStream zout = new ZipOutputStream(buf)) {
            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                if (skip.test(e.getName())) {
                    continue;
                }
                ZipEntry out = new ZipEntry(e.getName());
                out.setTime(0L);
                zout.putNextEntry(out);
                zin.transferTo(zout);
                zout.closeEntry();
            }
        }
        return buf.toByteArray();
    }
}
