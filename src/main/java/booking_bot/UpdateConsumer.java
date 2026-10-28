package booking_bot;

import booking_bot.Entity.BookingEntity;
import booking_bot.Entity.ServiceEntity;
import booking_bot.Repository.BookingRepository;
import booking_bot.Repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

@Component
public class UpdateConsumer implements LongPollingUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ServiceRepository serviceRepository; // Подключаем базу
    private final BookingRepository bookingRepository;

    public UpdateConsumer(@Value("${bot.token}") String botToken, ServiceRepository serviceRepository, BookingRepository bookingRepository) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.serviceRepository = serviceRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (text.equals("/start")) {
                    sendStartMenu(chatId);
                }
            }
            // Обработка нажатия на кнопки
            else if (update.hasCallbackQuery()) {
                String callData = update.getCallbackQuery().getData();
                long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (callData.equals("SHOW_SERVICES")) {
                    showServices(chatId);
                }

                else if (callData.startsWith("SELECT_SERVICE_")) {
                    Long serviceId = Long.parseLong(callData.replace("SELECT_SERVICE_", ""));
                    showDates(chatId, serviceId);
                }

                else if (callData.startsWith("SELECT_DATE_")) {
                    String[] parts = callData.split("_");
                    Long serviceId = Long.parseLong(parts[2]);
                    LocalDate date = LocalDate.parse(parts[3]);

                    showTimes(chatId, serviceId, date);
                }

                else if (callData.startsWith("BOOK_")) {
                    String[] parts = callData.split("_");
                    Long serviceId = Long.parseLong(parts[1]);
                    LocalDateTime startTime = LocalDateTime.parse(parts[2]);

                    bookTime(chatId, serviceId, startTime);
                }
            }
        }
    }

    // Меню с кнопкой
    private void sendStartMenu(long chatId) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder().text("Посмотреть услуги").callbackData("SHOW_SERVICES").build());

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Привет! Я бот для записи. Выбери действие:")
                .replyMarkup(new InlineKeyboardMarkup(List.of(row)))
                .build();
        execute(message);
    }

    // Вывод услуг из БД
    private void showServices(long chatId) {
        List<ServiceEntity> services = serviceRepository.findByActiveTrue();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ServiceEntity s : services) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text(s.getName() + " (" + s.getPrice() + " руб.)")
                    .callbackData("SELECT_SERVICE_" + s.getId()) // передаем ID услуги
                    .build());
            rows.add(row);
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Выберите услугу: ")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
        execute(message);
    }

    // Вспомогательный метод для отправки
    private void execute(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    //Показать даты
    private void showDates(long chatId, Long serviceId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM (EEE)", new Locale("ru"));

        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text(date.format(formatter))
                    .callbackData("SELECT_DATE_" + serviceId + "_" + date.toString())
                    .build());
            rows.add(row);
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Выберите дату:")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
        execute(message);
    }

    private void showTimes(long chatId, Long serviceId, LocalDate date) {
        // 1. Получаем все записи на этот день из базы
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59);
        List<BookingEntity> bookings = bookingRepository.findByStartTimeBetween(startOfDay, endOfDay);

        // 2. Генерируем сетку времени с 10:00 до 20:00 с шагом 30 минут
        List<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDateTime current = date.atTime(10, 0);
        LocalDateTime end = date.atTime(20, 0);

        while (current.isBefore(end)) {
            LocalDateTime slotTime = current;

            // 3. Проверяем, занято ли это время
            boolean isBusy = false;
            for (BookingEntity b : bookings) {
                // Если запись начинается в этот же момент или перекрывает его
                if (b.getStartTime().equals(slotTime)) {
                    isBusy = true;
                    break;
                }
            }

            // 4. Если время свободно — добавляем кнопку
            if (!isBusy) {
                InlineKeyboardRow row = new InlineKeyboardRow();
                // Форматируем время красиво (например, 10:00)
                String timeStr = String.format("%02d:%02d", slotTime.getHour(), slotTime.getMinute());

                row.add(InlineKeyboardButton.builder()
                        .text(timeStr)
                        .callbackData("BOOK_" + serviceId + "_" + slotTime.toString())
                        .build());
                rows.add(row);
            }

            current = current.plusMinutes(30); // шаг 30 минут
        }

        if (rows.isEmpty()) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text("На эту дату нет свободного времени. Выберите другую.")
                    .build();
            execute(message);
        } else {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text("Выберите время на " + date + ":")
                    .replyMarkup(new InlineKeyboardMarkup(rows))
                    .build();
            execute(message);
        }
    }

    private void bookTime(long chatId, Long serviceId, LocalDateTime startTime) {
        // 1. Сохраняем запись в базу
        BookingEntity booking = new BookingEntity();
        booking.setTelegramUserId(chatId);
        booking.setServiceId(serviceId);
        booking.setStartTime(startTime);
        bookingRepository.save(booking);

        // 2. Подтверждаем клиенту
        SendMessage toClient = SendMessage.builder()
                .chatId(chatId)
                .text("Вы успешно записаны на " + startTime.toString().replace("T", " ") + "!")
                .build();
        execute(toClient);

        // 3. Отправляем уведомление ВЛАДЕЛЬЦУ (тебе)
        // Вставь свой Telegram ID в переменную OWNER_ID ниже
        long ownerId = 8355624591L; // <--- ЗАМЕНИ НА СВОЙ ID
        SendMessage toOwner = SendMessage.builder()
                .chatId(ownerId)
                .text("🔥 НОВАЯ ЗАПИСЬ!\nКлиент ID: " + chatId + "\nУслуга ID: " + serviceId +
                        "\nВремя: " + startTime.toString().replace("T", " "))
                .build();
        execute(toOwner);
    }
}