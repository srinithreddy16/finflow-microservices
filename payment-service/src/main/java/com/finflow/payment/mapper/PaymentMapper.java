package com.finflow.payment.mapper;

import com.finflow.payment.dto.LedgerEntryDto;
import com.finflow.payment.dto.PaymentResponseDto;
import com.finflow.payment.model.EntryType;
import com.finflow.payment.model.LedgerEntry;
import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponseDto toDto(Payment payment);

    LedgerEntryDto toDto(LedgerEntry ledgerEntry);

    List<PaymentResponseDto> toDtoList(List<Payment> payments);

    List<LedgerEntryDto> toLedgerDtoList(List<LedgerEntry> entries);

    default String map(PaymentStatus status) {
        return status != null ? status.name() : null;
    }

    default String map(EntryType entryType) {
        return entryType != null ? entryType.name() : null;
    }
}
