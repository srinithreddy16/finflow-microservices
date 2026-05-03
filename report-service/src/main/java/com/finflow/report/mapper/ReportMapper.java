package com.finflow.report.mapper;

import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.model.Report;
import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.model.ReportType;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    @Mapping(target = "downloadUrl", expression = "java(null)")
    @Mapping(target = "expiresAt", expression = "java(null)")
    ReportResponseDto toDto(Report report);

    List<ReportResponseDto> toDtoList(List<Report> reports);

    default String map(ReportStatus s) {
        return s != null ? s.name() : null;
    }

    default String map(ReportType t) {
        return t != null ? t.name() : null;
    }

    default String map(ReportFormat f) {
        return f != null ? f.name() : null;
    }
}
