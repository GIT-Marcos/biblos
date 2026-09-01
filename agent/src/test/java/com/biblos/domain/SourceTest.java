package com.biblos.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Source")
class SourceTest {

    @Test
    @DisplayName("constructor should set all fields when all provided")
    void constructor_shouldSetAllFields_when_allProvided() {
        Source source = new Source(1, "book.pdf", "Author/book.pdf",
                "author/book.pdf", "abc123", "PDF", 10L, 2024, "1st",
                "https://example.com", "2024-01-01", "2024-06-01", "2024-12-01");

        assertThat(source.id()).isEqualTo(1);
        assertThat(source.name()).isEqualTo("book.pdf");
        assertThat(source.path()).isEqualTo("Author/book.pdf");
        assertThat(source.pathLower()).isEqualTo("author/book.pdf");
        assertThat(source.contentHash()).isEqualTo("abc123");
        assertThat(source.fileFormat()).isEqualTo("PDF");
        assertThat(source.authorId()).isEqualTo(10L);
        assertThat(source.year()).isEqualTo(2024);
        assertThat(source.edition()).isEqualTo("1st");
        assertThat(source.url()).isEqualTo("https://example.com");
        assertThat(source.createdAt()).isEqualTo("2024-01-01");
        assertThat(source.updatedAt()).isEqualTo("2024-06-01");
        assertThat(source.deletedAt()).isEqualTo("2024-12-01");
    }

    @Test
    @DisplayName("constructor should accept nulls when optional fields are null")
    void constructor_shouldAcceptNulls_when_optionalFieldsNull() {
        Source source = new Source(1, "book.pdf", "Author/book.pdf",
                "author/book.pdf", "abc123", "PDF", null, null, null, null,
                "2024-01-01", "2024-06-01", null);

        assertThat(source.authorId()).isNull();
        assertThat(source.year()).isNull();
        assertThat(source.edition()).isNull();
        assertThat(source.url()).isNull();
        assertThat(source.deletedAt()).isNull();
    }

    @Test
    @DisplayName("equals should be equal when same values")
    void equals_shouldBeEqual_when_sameValues() {
        Source a = new Source(1, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);
        Source b = new Source(1, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("equals should not be equal when different id")
    void equals_shouldNotBeEqual_when_differentId() {
        Source a = new Source(1, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);
        Source b = new Source(2, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals should not be equal when different contentHash")
    void equals_shouldNotBeEqual_when_differentHash() {
        Source a = new Source(1, "book.pdf", "path", "path_lower", "hash1", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);
        Source b = new Source(1, "book.pdf", "path", "path_lower", "hash2", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("hashCode should be consistent when equal objects")
    void hashCode_shouldBeConsistent_when_equalObjects() {
        Source a = new Source(1, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);
        Source b = new Source(1, "book.pdf", "path", "path_lower", "hash", "PDF",
                null, null, null, null, "2024-01-01", "2024-06-01", null);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("toString should contain field names when called")
    void toString_shouldContainFieldName_when_called() {
        Source source = new Source(1, "book.pdf", "Author/book.pdf",
                "author/book.pdf", "abc123", "PDF", null, null, null, null,
                "2024-01-01", "2024-06-01", null);

        String str = source.toString();

        assertThat(str).contains("book.pdf");
        assertThat(str).contains("abc123");
    }

    @Test
    @DisplayName("accessors should return exact values when created")
    void accessors_shouldReturnExactValues_when_created() {
        Long authorId = 42L;
        Integer year = 1999;
        Source source = new Source(99, "name", "path", "pathLower", "hash", "EPUB",
                authorId, year, "ed", "url", "created", "updated", "deleted");

        assertThat(source.id()).isEqualTo(99);
        assertThat(source.authorId()).isSameAs(authorId);
        assertThat(source.year()).isSameAs(year);
    }
}
