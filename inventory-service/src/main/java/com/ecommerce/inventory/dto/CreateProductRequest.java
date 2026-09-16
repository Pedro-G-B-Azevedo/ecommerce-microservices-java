package com.ecommerce.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Dados para cadastro de um produto")
public record CreateProductRequest(

        @Schema(example = "TEC-MEC-001")
        @NotBlank(message = "sku é obrigatório")
        @Size(max = 50, message = "sku aceita no máximo 50 caracteres")
        String sku,

        @Schema(example = "Teclado mecânico ABNT2")
        @NotBlank(message = "name é obrigatório")
        @Size(max = 200, message = "name aceita no máximo 200 caracteres")
        String name,

        @Schema(example = "Switches lineares, layout ABNT2")
        String description,

        @Schema(example = "349.90")
        @NotNull(message = "price é obrigatório")
        @DecimalMin(value = "0.00", message = "price não pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "price aceita no máximo duas casas decimais")
        BigDecimal price,

        @Schema(example = "50", description = "Quantidade inicial em estoque")
        @NotNull(message = "initialQuantity é obrigatório")
        @PositiveOrZero(message = "initialQuantity não pode ser negativa")
        Integer initialQuantity) {
}
