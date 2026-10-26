package com.claimstream.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The baseline: one thread reads a line, handles it, then reads the next. Simple, correct, and slow when I/O waits. */
public final class SingleThreadedPipeline implements IntakePipeline {

    @Override
    public String name() {
        return "Single thread";
    }

    @Override
    public void process(Path batchFile, LineHandler handler) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(batchFile)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (IntakePipeline.isClaimLine(line)) {
                    handler.handle(line);
                }
            }
        }
    }
}
