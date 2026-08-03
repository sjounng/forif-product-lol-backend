package com.scrim.lolscrim.domain.session.dto;

import jakarta.validation.constraints.Size;

public record RejectSessionRequest(@Size(max = 500) String reason) {
}
