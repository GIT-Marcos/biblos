package com.biblos.domain;

import java.nio.file.Path;

public class AuthorInferrer {

    private AuthorInferrer() {
    }

    public static String infer(Path rootDir, Path file) {
        Path relative = rootDir.relativize(file).normalize();

        if (relative.getNameCount() == 0) {
            return null;
        }

        String author = relative.getName(0).toString();

        if (author.equals(".") || author.equals("..")) {
            return null;
        }

        author = author.strip();
        return author.isEmpty() ? null : author;
    }
}
