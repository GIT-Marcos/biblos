package com.biblos.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("AuthorInferrer")
class AuthorInferrerTest {

    private static final Path ROOT = Paths.get("/library");

    @ParameterizedTest(name = "infer(\"{0}\") → \"{1}\"")
    @DisplayName("infer should return first folder segment as author")
    @CsvSource({
            "Author1/libro.pdf,              Author1",
            "Gabriel Garcia Marquez/Cien Anos.pdf, Gabriel Garcia Marquez",
            "Anonimo/Medieval/texto.pdf,      Anonimo",
            "subcarpeta/libro.pdf,            subcarpeta"
    })
    void infer_shouldReturnAuthor_when_normalSubfolder(String relativePath, String expectedAuthor) {
        Path file = ROOT.resolve(relativePath);

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo(expectedAuthor);
    }

    @Test
    @DisplayName("infer should return filename when file is directly in root")
    void infer_shouldReturnFileName_when_fileInRoot() {
        Path file = ROOT.resolve("libro.pdf");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("libro.pdf");
    }

    @Test
    @Tag("edge-case")
    @DisplayName("infer should normalize dot path and return filename")
    void infer_shouldNormalizeDotPath_when_firstSegmentIsDot() {
        Path file = ROOT.resolve("./libro.pdf");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("libro.pdf");
    }

    @Test
    @Tag("edge-case")
    @DisplayName("infer should normalize double dot path and return author")
    void infer_shouldNormalizeDoubleDotPath_when_pathHasTraversal() {
        Path file = ROOT.resolve("sub/../Autor/file.pdf");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("Autor");
    }

    @Test
    @DisplayName("infer should preserve original casing of folder name")
    void infer_shouldPreserveCasing_when_folderNameHasMixedCase() {
        Path file = ROOT.resolve("Anonimo/Medieval/texto.pdf");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("Anonimo");
    }

    @Test
    @DisplayName("infer should return single segment folder name")
    void infer_shouldReturnSingleSegment_when_oneLevelDeep() {
        Path file = ROOT.resolve("Subfolder/book.epub");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("Subfolder");
    }

    @Test
    @DisplayName("infer should return deeply nested author name")
    void infer_shouldReturnFirstSegment_when_pathIsDeeplyNested() {
        Path file = ROOT.resolve("Author Name/Category/Sub/book.pdf");

        String result = AuthorInferrer.infer(ROOT, file);

        assertThat(result).isEqualTo("Author Name");
    }
}
