package com.mecong.integrationcheckerstorage.data;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Data
@Entity(name = "dtos")
public class DtoEntity {
    @Id
    private String id;
    private String dtoFields;
}
