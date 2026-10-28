package booking_bot;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

@Component
public class BookingBot implements SpringLongPollingBot {

    private final UpdateConsumer updateConsumer;
    private final String botToken;
    private final String botUsername;

    public BookingBot(UpdateConsumer updateConsumer,
                      @Value("${bot.token}") String botToken,
                      @Value("${bot.username}") String botUsername) {
        this.updateConsumer = updateConsumer;
        this.botToken = botToken;
        this.botUsername = botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updateConsumer;
    }
}