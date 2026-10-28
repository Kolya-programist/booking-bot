package booking_bot.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "services")
@Data
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;             // Название услуги
    private Integer price;           // Цена
    private Integer durationMinutes; // Длительность в минутах
    private boolean active = true;   // Активна ли услуга
}