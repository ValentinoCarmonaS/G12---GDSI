package com.easyeat.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record DemographicsRequest(
        @Min(value = 14, message = "La edad debe ser un número entero entre 14 y 120 años.")
        @Max(value = 120, message = "La edad debe ser un número entero entre 14 y 120 años.")
        Integer age,

        String gender,

        @Size(max = 255, message = "El domicilio no puede exceder los 255 caracteres.")
        String address
) {
}

