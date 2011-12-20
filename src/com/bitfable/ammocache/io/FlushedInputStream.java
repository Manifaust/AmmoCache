package com.bitfable.ammocache.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * According to Gilles Debunne, previous versions of BitmapFactory.decodeStream
 * don't work well with the regular InputStream class when using a slow
 * connection. This class is meant to fix that problem.
 */
public class FlushedInputStream extends FilterInputStream {
    public FlushedInputStream(InputStream inputStream) {
        super(inputStream);
    }

    @Override
    public long skip(long n) throws IOException {
        long totalBytesSkipped = 0L;
        while (totalBytesSkipped < n) {
            long bytesSkipped = in.skip(n - totalBytesSkipped);
            if (bytesSkipped == 0L) {
                  int byteRead = read();
                  if (byteRead < 0) {
                      break;  // we reached EOF
                  } else {
                      bytesSkipped = 1; // we read one byte
                  }
           }
            totalBytesSkipped += bytesSkipped;
        }
        return totalBytesSkipped;
    }

}
