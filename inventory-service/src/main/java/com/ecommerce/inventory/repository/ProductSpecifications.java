package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Product;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /** Busca por parte do nome ou do SKU, sem diferenciar maiúsculas. */
    public static Specification<Product> matches(String term) {
        String pattern = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("sku")), pattern));
    }
}
