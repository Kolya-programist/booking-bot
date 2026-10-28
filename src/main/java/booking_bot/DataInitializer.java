//Этот класс создаеться только для тестовых проектов чтоб сразу была какая-то база заказов,
// в реальном проекте не надо!!!

package booking_bot;

import booking_bot.Entity.ServiceEntity;
import booking_bot.Repository.ServiceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ServiceRepository serviceRepository;

    public DataInitializer(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void run(String... args) {
        if (serviceRepository.count() == 0) {
            createService("Мужская стрижка", 1500, 45);
            createService("Женская стрижка", 2500, 60);
            createService("Маникюр", 2000, 90);
        }
    }

    private void createService(String name, int price, int duration) {
        ServiceEntity s = new ServiceEntity();
        s.setName(name);
        s.setPrice(price);
        s.setDurationMinutes(duration);
        serviceRepository.save(s);
    }
}