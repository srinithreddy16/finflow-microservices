package com.finflow.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Portable pagination wrapper aligned with Spring Data {@link Page} metadata.
 *
 * @param <T> element type of the current page
 */
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;
    private final boolean first;

    public PagedResponse(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last,
            boolean first) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
        this.first = first;
    }

    public static <T> PagedResponse<T> from(Page<?> springPage, List<T> content) {
        return new PagedResponse<>(
                content,
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast(),
                springPage.isFirst());
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isLast() {
        return last;
    }

    public boolean isFirst() {
        return first;
    }
}
