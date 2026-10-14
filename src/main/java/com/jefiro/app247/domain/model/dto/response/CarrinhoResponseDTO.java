package com.jefiro.app247.domain.model.dto.response;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.enum_type.CarrinhoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CarrinhoResponseDTO(

        String carrinhoId,

        String terminalId,

        CarrinhoStatus status,

        BigDecimal subtotal,

        boolean customerLinked,

        String customerDisplayName,

        List<ItemResponseDTO> items,

        Instant createdAt,

        Instant updatedAt

) {
    public CarrinhoResponseDTO(Carrinho car) {
        this(car.getIdCarrinho(), car.getIdTerminal(), car.getStatus(), car.getSubtotal(),
                car.getUser()!=null,car.getUser()!=null?car.getUser().getNome():null,
                car.getItems().stream().map(ItemResponseDTO::new).toList(), car.getCreatedAt(), car.getUpdatedAt());
    }
}
