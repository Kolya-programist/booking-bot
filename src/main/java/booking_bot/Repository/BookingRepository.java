package booking_bot.Repository;

import booking_bot.Entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    // Ищем записи на конкретный день, чтобы понять, какие слоты заняты
    List<BookingEntity> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}