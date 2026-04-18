package com.finflow.transaction.mapper;

import com.finflow.transaction.dto.TransactionRequestDto;
import com.finflow.transaction.dto.TransactionResponseDto;
import com.finflow.transaction.model.Transaction;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Named("toDto")
    @Mapping(
            target = "status",
            expression =
                    "java(transaction.getStatus() == null ? null : transaction.getStatus().name())")
    TransactionResponseDto toDto(Transaction transaction);

    @IterableMapping(qualifiedByName = "toDto")
    List<TransactionResponseDto> toDtoList(List<Transaction> transactions);

    @Mapping(
            target = "status",
            expression = "java(transaction.getStatus().name())")
    TransactionResponseDto toDtoWithStatusString(Transaction transaction);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Transaction toEntity(TransactionRequestDto dto);
}
