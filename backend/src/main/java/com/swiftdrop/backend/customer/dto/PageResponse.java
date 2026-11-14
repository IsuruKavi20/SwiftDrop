package com.swiftdrop.backend.customer.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of results with a stable JSON shape. Spring Data's own Page is not meant to be returned as JSON
 * (its format can change between versions), so APIs return this instead.
 */
// Known shortcut (docs/MODULES.md): notification reads this directly. To be replaced by a small API or events;
// listed here so the module guard allows only this, not the rest of the package.
@org.springframework.modulith.NamedInterface("shortcut-PageResponse")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
