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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
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

    // Карта, которая запоминает, на каком шаге находится клиент: chatId -> (услуга, время)
    private final java.util.Map<Long, Object[]> waitingForData = new java.util.concurrent.ConcurrentHashMap<>();

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

                // Если клиент в процессе записи (пишет имя или телефон)
                if (waitingForData.containsKey(chatId)) {
                    handleNameAndPhone(chatId, text);
                    return; // Останавливаем дальнейшую обработку
                }

                if (text.equals("/start")) {
                    long messageId = update.getMessage().getMessageId();
                    deleteMessage(chatId, (int) messageId); // удаляем сообщение с командой /start
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
                    long messageId = update.getCallbackQuery().getMessage().getMessageId();
                    showDates(chatId, serviceId, (int) messageId);
                }

                else if (callData.startsWith("SELECT_DATE_")) {
                    String[] parts = callData.split("_");
                    Long serviceId = Long.parseLong(parts[2]);
                    LocalDate date = LocalDate.parse(parts[3]);

                    long messageId = update.getCallbackQuery().getMessage().getMessageId();
                    showTimes(chatId, serviceId, date, (int) messageId);
                }

                else if (callData.startsWith("BOOK_")) {
                    String[] parts = callData.split("_");
                    Long serviceId = Long.parseLong(parts[1]);
                    LocalDateTime startTime = LocalDateTime.parse(parts[2]);

                    long messageId = update.getCallbackQuery().getMessage().getMessageId();
                    bookTime(chatId, serviceId, startTime, (int) messageId);
                }

                else if (callData.equals("MY_BOOKINGS")) {
                    showMyBookings(chatId);
                }
                else if (callData.startsWith("CANCEL_")) {
                    Long bookingId = Long.parseLong(callData.replace("CANCEL_", ""));
                    long messageId = update.getCallbackQuery().getMessage().getMessageId();
                    cancelBooking(chatId, bookingId, (int) messageId);
                }
            }
        }
    }

    // Меню с кнопкой
    private void sendStartMenu(long chatId) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder().text("Посмотреть услуги").callbackData("SHOW_SERVICES").build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder().text("Мои записи").callbackData("MY_BOOKINGS").build());

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Привет! Я бот для записи. Выбери действие:")
                .replyMarkup(new InlineKeyboardMarkup(List.of(row1, row2)))
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
    private void showDates(long chatId, Long serviceId, int messageId) {
        deleteMessage(chatId, messageId);

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

    private void showTimes(long chatId, Long serviceId, LocalDate date, int messageId) {
        deleteMessage(chatId, messageId);

        // 1. Получаем услугу, чтобы узнать её длительность
        ServiceEntity service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) return;
        int duration = service.getDurationMinutes();

        // 2. Получаем все записи на этот день
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59);
        List<BookingEntity> bookings = bookingRepository.findByStartTimeBetween(startOfDay, endOfDay);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        LocalDateTime current = date.atTime(10, 0);
        LocalDateTime end = date.atTime(20, 0);

        while (current.isBefore(end)) {
            LocalDateTime slotStart = current;
            LocalDateTime slotEnd = slotStart.plusMinutes(duration); // Конец нового слота

            boolean isBusy = false;
            for (BookingEntity b : bookings) {
                // Для старых записей нам тоже нужна длительность их услуги
                ServiceEntity oldService = serviceRepository.findById(b.getServiceId()).orElse(null);
                int oldDuration = (oldService != null) ? oldService.getDurationMinutes() : 30;

                LocalDateTime bookingStart = b.getStartTime();
                LocalDateTime bookingEnd = bookingStart.plusMinutes(oldDuration);

                // Проверка пересечения отрезков времени
                if (slotStart.isBefore(bookingEnd) && slotEnd.isAfter(bookingStart)) {
                    isBusy = true;
                    break;
                }
            }

            if (!isBusy) {
                InlineKeyboardRow row = new InlineKeyboardRow();
                String timeStr = String.format("%02d:%02d", slotStart.getHour(), slotStart.getMinute());
                row.add(InlineKeyboardButton.builder()
                        .text(timeStr)
                        .callbackData("BOOK_" + serviceId + "_" + slotStart.toString())
                        .build());
                rows.add(row);
            }
            current = current.plusMinutes(10);
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

    private void bookTime(long chatId, Long serviceId, LocalDateTime startTime, Integer messageId) {
        // Сохраняем выбор во временную память
        waitingForData.put(chatId, new Object[]{serviceId, startTime});

        // Удаляем сообщение с кнопками
        deleteMessage(chatId, messageId);

        // Просим имя
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Укажите ФИО? (Напишите ответным сообщением)")
                .build();
        execute(message);
    }

    // Показать записи клиента
    private void showMyBookings(long chatId) {
        List<BookingEntity> bookings = bookingRepository.findByTelegramUserId(chatId);

        if (bookings.isEmpty()) {
            SendMessage msg = SendMessage.builder().chatId(chatId).text("У вас пока нет записей.").build();
            execute(msg);
            return;
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (BookingEntity b : bookings) {
            String timeStr = b.getStartTime().toString().replace("T", " ");
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text("❌ Отменить запись на " + timeStr)
                    .callbackData("CANCEL_" + b.getId())
                    .build());
            rows.add(row);
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Ваши активные записи (нажмите, чтобы отменить):")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
        execute(message);
    }

    // Отмена записи
    private void cancelBooking(long chatId, Long bookingId, int messageId) {
        deleteMessage(chatId, messageId);

        if (bookingRepository.existsById(bookingId)) {
            bookingRepository.deleteById(bookingId);

            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ Ваша запись успешно отменена.")
                    .build();
            execute(message);
            sendStartMenu(chatId);
        }
    }

    private void deleteMessage(long chatId, int messageId) {
        try {
            DeleteMessage deleteMessage = DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build();
            telegramClient.execute(deleteMessage);
        } catch (TelegramApiException e) {
            // Если сообщение старше 48 часов, Телеграм не даст его удалить, просто игнорируем ошибку
            System.out.println("Не удалось удалить сообщение: " + e.getMessage());
        }
    }

    private void handleNameAndPhone(long chatId, String text) {
        Object[] data = waitingForData.get(chatId);
        Long serviceId = (Long) data[0];
        LocalDateTime startTime = (LocalDateTime) data[1];

        // Если в массиве 2 элемента (услуга, время) — значит мы только что получили ИМЯ
        if (data.length == 2) {
            // Меняем массив на 3 элемента: услуга, время, имя
            waitingForData.put(chatId, new Object[]{serviceId, startTime, text});

            SendMessage msg = SendMessage.builder()
                    .chatId(chatId)
                    .text("Отлично, " + text + "! Теперь напишите ваш номер телефона (или нажмите /start для отмены).")
                    .build();
            execute(msg);
        }
        // Если в массиве 3 элемента — значит мы получили ТЕЛЕФОН
        else if (data.length == 3) {
            String name = (String) data[2];
            String phone = text;

            // Сохраняем в базу
            BookingEntity booking = new BookingEntity();
            booking.setTelegramUserId(chatId);
            booking.setServiceId(serviceId);
            booking.setStartTime(startTime);
            booking.setClientName(name);
            booking.setClientPhone(phone);
            bookingRepository.save(booking);

            waitingForData.remove(chatId); // Очищаем память

            // Подтверждаем клиенту
            SendMessage toClient = SendMessage.builder()
                    .chatId(chatId)
                    .text("✅ Вы записаны!\nИмя: " + name + "\nТелефон: " + phone + "\nВремя: " + startTime.toString().replace("T", " "))
                    .build();
            execute(toClient);

            // Уведомляем владельца
            long ownerId = 8355624591L; // Твой ID
            SendMessage toOwner = SendMessage.builder()
                    .chatId(ownerId)
                    .text("🔥 НОВАЯ ЗАПИСЬ!\nИмя: " + name + "\nТелефон: " + phone + "\nВремя: " + startTime.toString().replace("T", " "))
                    .build();
            execute(toOwner);

            sendStartMenu(chatId);
        }
    }
}