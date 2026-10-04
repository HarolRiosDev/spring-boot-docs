package dev.springbootdocs.examples.tasks.dto;

import java.util.List;
import org.springframework.data.domain.Slice;

// Spring Data no trae un formato estable para Slice (PagedModel solo acepta Page)
public record SliceResponse<T>(List<T> content, int number, int size, boolean hasNext) {

    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return new SliceResponse<>(slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
