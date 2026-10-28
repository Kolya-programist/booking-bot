package booking_bot;

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

import java.util.List;

@Component
public class UpdateConsumer implements LongPollingUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ServiceRepository serviceRepository; // Подключаем базу

    public UpdateConsumer(@Value("${bot.token}") String botToken, ServiceRepository serviceRepository) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.serviceRepository = serviceRepository;
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
        StringBuilder sb = new StringBuilder("Наши услуги:\n\n");

        for (ServiceEntity s : services) {
            sb.append("💇 ").append(s.getName())
                    .append(" — ").append(s.getPrice()).append(" руб. (")
                    .append(s.getDurationMinutes()).append(" мин)\n");
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
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
}