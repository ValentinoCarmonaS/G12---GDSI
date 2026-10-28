package com.easyeat.catalog.dto;

import java.util.ArrayList;
import java.util.List;

public class DinerCatalogResponse {

    private EstablishmentDetailDto establishment;
    private List<CategoryGroupDto> categories = new ArrayList<>();

    public DinerCatalogResponse() {
    }

    public DinerCatalogResponse(EstablishmentDetailDto establishment, List<CategoryGroupDto> categories) {
        this.establishment = establishment;
        this.categories = categories != null ? categories : new ArrayList<>();
    }

    public EstablishmentDetailDto getEstablishment() {
        return establishment;
    }

    public void setEstablishment(EstablishmentDetailDto establishment) {
        this.establishment = establishment;
    }

    public List<CategoryGroupDto> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryGroupDto> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
    }
}

