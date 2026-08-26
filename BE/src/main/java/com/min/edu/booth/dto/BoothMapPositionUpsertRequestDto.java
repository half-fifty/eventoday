package com.min.edu.booth.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothMapPositionUpsertRequestDto {

    // 부스 수가 비정상적으로 큰 요청 한 번이 다수의 DB 조회·긴 트랜잭션으로 이어지지 않도록 상한을 둔다.
    @NotEmpty
    @Size(max = 500)
    @Valid
    private List<PositionItem> positions;

    @Getter
    @NoArgsConstructor
    public static class PositionItem {

        @NotNull
        private Long boothId;

        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        @DecimalMax(value = "1", inclusive = true)
        private BigDecimal xRatio;

        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        @DecimalMax(value = "1", inclusive = true)
        private BigDecimal yRatio;
    }
}
