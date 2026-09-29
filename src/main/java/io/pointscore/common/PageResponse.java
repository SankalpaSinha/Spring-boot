package io.pointscore.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable pagination envelope.
 *
 * <p>Spring's own {@code Page} serialises its entire internal structure, which
 * is not a contract anyone should depend on -- its JSON shape has changed
 * between Spring versions and would drag clients along with it. This exposes
 * only the fields the API promises.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
