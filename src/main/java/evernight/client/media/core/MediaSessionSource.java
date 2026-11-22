package evernight.client.media.core;

import java.io.IOException;

public interface MediaSessionSource extends AutoCloseable {
    MediaSnapshot read() throws IOException;

    @Override
    void close() throws IOException;
}