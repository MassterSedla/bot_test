package org.example;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetFileResponse;
import jakarta.mail.MessagingException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Bot {

    private final static String botToken = "6732682706:AAEHk2B03YcIR3RY-EWGt3Q8PAL_0m0CINE";

    private final TelegramBot bot;

    public Bot() {
        bot = new TelegramBot(botToken);
        setBotCommands();
    }

    public void mainMethod() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    String messageText = update.message().text();
                    long chatId = update.message().chat().id();

                    if (messageText.equals("/start")) {
                        start(chatId);
                    } else if (messageText.equals("/qwerty")) {
                        qwerty(chatId);
                    }

                } else if (update.message() != null && update.message().document() != null) {
                    try {
                        handleDocument(update.message().document(), update.message().chat().id());
                    } catch (MessagingException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if(update.callbackQuery() != null) {
                    setUpdatesListener(update);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public void start(long chatId) {
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("Email sandler").callbackData("Email sandler"),
                new InlineKeyboardButton("Test sandler").callbackData("Test sandler"));
        SendMessage request = new SendMessage(chatId, "Choose an option:")
                .replyMarkup(inlineKeyboard);
        bot.execute(request);
    }

    public void qwerty(long chatId) {
        ReplyKeyboardMarkup replyKeyboard = new ReplyKeyboardMarkup(
                new KeyboardButton[] {
                        new KeyboardButton("Command 1"),
                        new KeyboardButton("Command 2")
                },
                new KeyboardButton[] {
                        new KeyboardButton("Command 3")
                }
        ).oneTimeKeyboard(true)
                .resizeKeyboard(true)
                .selective(true);

        SendMessage request = new SendMessage(chatId, "Choose a command:")
                .replyMarkup(replyKeyboard);
        bot.execute(request);
    }

    public void setUpdatesListener(Update update) {
        String callbackData = update.callbackQuery().data();
        long messageId = update.callbackQuery().message().messageId();
        long chatId = update.callbackQuery().message().chat().id();
        String callId = update.callbackQuery().id();

        switch (callbackData) {
            case "Email sandler":
                EmailSender.clean();
                EmailSender.setTest(false);
                bot.execute(new EditMessageText(chatId, Math.toIntExact(messageId), "Пришлите exel файл с данными"));
                break;
            case "Test sandler":
                EmailSender.clean();
                EmailSender.setTest(true);
                bot.execute(new EditMessageText(chatId, Math.toIntExact(messageId), "Пришлите exel файл с данными"));
                break;
            default:
                // Опционально: изменить текст сообщения после нажатия кнопки
                bot.execute(new EditMessageText(chatId, Math.toIntExact(messageId), "Вы выбрали: " + callbackData));
        }

    }

    public void setBotCommands() {
        String data = "{\"commands\":[{\"command\":\"start\",\"description\":\"Начать работу с ботом\"}," +
                "{\"command\":\"help\",\"description\":\"Получить помощь\"}," +
                "{\"command\":\"qwerty\",\"description\":\"сделать запрос\"}]}";
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/setMyCommands");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = data.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDocument(Document document, long chatId) throws MessagingException, IOException {
        String fileId = document.fileId();
        GetFile getFileRequest = new GetFile(fileId);
        GetFileResponse getFileResponse = bot.execute(getFileRequest);

        if (getFileResponse.isOk()) {
            String filePath = getFileResponse.file().filePath();
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
            File downloadedFile = new File("downloaded_" + document.fileName());
            try (InputStream in = new URL(fileUrl).openStream();
                 BufferedInputStream bis = new BufferedInputStream(in);
                 FileOutputStream fos = new FileOutputStream(downloadedFile)) {

                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = bis.read(dataBuffer, 0, 1024)) != -1) {
                    fos.write(dataBuffer, 0, bytesRead);
                }
                SendMessage message = new SendMessage(chatId, "Файл успешно загружен!");
                bot.execute(message);

            } catch (Exception e) {
                e.printStackTrace();
                SendMessage message = new SendMessage(chatId, "Произошла ошибка при загрузке файла.");
                bot.execute(message);
            }
            String mes = "";
            if (EmailSender.getExelFile() == null) {
                EmailSender.setExelFile(downloadedFile);
                mes = "Пришлите docx файл с текстом на русском";
            } else if (EmailSender.getTextFileRu() == null) {
                EmailSender.setTextFileRu(downloadedFile);
                mes = "Пришлите docx файл с текстом на английском";
            } else if (EmailSender.getTextFileEn() == null) {
                EmailSender.setTextFileEn(downloadedFile);
                mes = """
                        Пришлите docx файл с таким содержанием:
                        email1@mail.com,email2@mail.com  -  1 - строка для установки скрытых получателей
                        email1@mail.com  -  2 строка - для установки адреса отправителя
                        ytiu rlbj male drre  -  3 строка - для установки одноразового пароля для установки соединения с почтой (настройки гугл почты -> пароли приложений -> создать, для этого необходимо на почтей включить двухэтапнаю аутентификацию)
                        """;
            } else if (EmailSender.getData() == null) {
                EmailSender.setData(downloadedFile);
                EmailSender.mainMethod();
                mes = "Рассылка осуществленна!";
            }
            SendMessage message = new SendMessage(chatId, mes);
            bot.execute(message);
        }
    }


}
