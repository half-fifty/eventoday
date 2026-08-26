package com.min.edu.admission.dto;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import java.time.OffsetDateTime;

public interface AdmissionLogView {
    Long getAdmissionLogId();
    Long getAdmissionTicketId();
    AdmissionAction getAction();
    AdmissionResult getResult();
    String getGateName();
    String getStaffNickname();
    OffsetDateTime getProcessedAt();
}
