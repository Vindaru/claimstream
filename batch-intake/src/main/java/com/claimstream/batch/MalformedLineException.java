package com.claimstream.batch;

/** A line that can't be turned into a claim. Counted and skipped; one bad line never stops the batch. */
public class MalformedLineException extends RuntimeException {

    public MalformedLineException(String line, Throwable cause) {
        super("Malformed claim line: " + line, cause);
    }
}
