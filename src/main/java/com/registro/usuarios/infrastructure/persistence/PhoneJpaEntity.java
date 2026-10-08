package com.registro.usuarios.infrastructure.persistence;

import com.registro.usuarios.domain.model.Phone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Modelo de persistencia de un teléfono de un usuario. La clave la genera la base de datos: los
 * teléfonos nunca se exponen y nunca se crean fuera de ella. Las claves de identidad crecen con el
 * orden de inserción, lo que conserva el orden en que se enviaron.
 */
@Entity
@Table(name = "phones")
class PhoneJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  // El lado propietario de la asociación: Hibernate lo lee para escribir user_id, el código nunca
  // lo hace.
  @SuppressWarnings("UnusedVariable")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private UserJpaEntity user;

  @Column(name = "phone_number", nullable = false, length = Phone.NUMBER_MAX_LENGTH)
  private String number;

  @Column(name = "city_code", nullable = false, length = Phone.CODE_MAX_LENGTH)
  private String cityCode;

  @Column(name = "country_code", nullable = false, length = Phone.CODE_MAX_LENGTH)
  private String countryCode;

  /** Requerido por JPA. */
  protected PhoneJpaEntity() {}

  PhoneJpaEntity(UserJpaEntity user, String number, String cityCode, String countryCode) {
    this.user = user;
    this.number = number;
    this.cityCode = cityCode;
    this.countryCode = countryCode;
  }

  @Override
  public String toString() {
    return "PhoneJpaEntity[id=" + id + "]";
  }
}
