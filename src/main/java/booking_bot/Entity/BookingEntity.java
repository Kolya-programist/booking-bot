package booking_bot.Entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramUserId; // Кто записался
    private Long serviceId;      // На какую услугу
    private LocalDateTime startTime; // Дата и время начала
    private String clientName;
    private String clientPhone;
}