package com.claimstream.batch;

/** The work done for one line of the batch file. Pipelines decide how many lines run at once. */
@FunctionalInterface
public interface LineHandler {

    void handle(String line);
}
