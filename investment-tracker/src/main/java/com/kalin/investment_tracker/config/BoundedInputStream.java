package com.kalin.investment_tracker.config;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Rejects an oversized response, including chunked bodies without Content-Length. */
public final class BoundedInputStream extends FilterInputStream {
    private final long maximumBytes;
    private long consumed;

    public BoundedInputStream(InputStream input, long maximumBytes) {
        super(input);
        if (maximumBytes < 1) throw new IllegalArgumentException("Response limit must be positive");
        this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
        ensureNotExceeded();
        int value = in.read();
        if (value != -1) count(1);
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        ensureNotExceeded();
        int allowed = (int) Math.min(length, maximumBytes - consumed + 1);
        int count = in.read(bytes, offset, allowed);
        if (count > 0) count(count);
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        if (count <= 0) return 0;
        byte[] buffer = new byte[(int) Math.min(8192, count)];
        long skipped = 0;
        while (skipped < count) {
            int read = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
            if (read == -1) break;
            skipped += read;
        }
        return skipped;
    }

    @Override public boolean markSupported() { return false; }
    @Override public synchronized void reset() throws IOException { throw new IOException("Reset is not supported"); }

    private void count(int size) throws IOException {
        consumed += size;
        if (consumed > maximumBytes) throw new IOException("External response exceeded the configured size limit");
    }

    private void ensureNotExceeded() throws IOException {
        if (consumed > maximumBytes) throw new IOException("External response exceeded the configured size limit");
    }
}
