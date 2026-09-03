package com.biblos.pipeline;

public class PipelineCancelledException extends RuntimeException {
    PipelineCancelledException(String message) {
        super(message);
    }
}

