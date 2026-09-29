package com.jobbeacon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ward")
public class Ward {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @Column(name = "name_local", nullable = false)
    private String nameLocal;

    @Column(name = "province_id", nullable = false, length = 20)
    private String provinceId;
}
