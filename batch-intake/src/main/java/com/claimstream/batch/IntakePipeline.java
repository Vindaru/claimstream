package com.claimstream.batch;

import java.io.IOException;
import java.nio.file.Path;

/** Feeds every claim line of a batch file to a {@link LineHandler}. Only the threading differs per pipeline. */
public interface IntakePipeline {

    String name();

    void process(Path batchFile, LineHandler handler) throws IOException, InterruptedException;

    /** Header and blank lines carry no claim. */
    static boolean isClaimLine(String line) {
        return !line.isBlank() && !line.startsWith("#");
    }
}
