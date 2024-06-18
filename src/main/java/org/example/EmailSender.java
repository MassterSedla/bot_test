package org.example;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.*;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public class EmailSender {
    private static boolean test;

    private static File data;
    private static String emails;
    private static String user;
    private static String password;
    private static File exelFile;
    private static File textFileRu;
    private static File textFileEn;

    public static void mainMethod() throws IOException, MessagingException {

        try (FileInputStream br = new FileInputStream(data);
             XWPFDocument document = new XWPFDocument(br);) {

            emails = document.getParagraphs().getFirst().getText();
            user = document.getParagraphs().get(1).getText();
            password = document.getParagraphs().get(2).getText();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Настройки SMTP сервера
        String host = "smtp.gmail.com";

        // Настройки свойств
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.trust", host);

        // Получение сессии
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });

        Map<String, String> rowsValue = new HashMap<>();
        List<String> rowsName = new ArrayList<>();
        String textRu = getText(textFileRu);
        String textEn = getText(textFileEn);
        FileInputStream excelFile = new FileInputStream(exelFile);
        Workbook workbook = new XSSFWorkbook(excelFile);
        Sheet sheet = workbook.getSheetAt(0);
        int i = 0;
        for (Row row : sheet) {
            int j = 0;
            for (Cell cell : row) {
                if (i == 0) {
                    rowsName.add(getCellValueAsString(cell));
                    rowsValue.put(getCellValueAsString(cell), "");
                } else {
                    rowsValue.put(rowsName.get(j), getCellValueAsString(cell));
                }
                j++;
            }
            if (i > 0) {
                // Создание объекта MimeMessage
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(user));
                String to = rowsValue.get("<mail>");
                // Установка получателей
                if (!test) {
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                }
                message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(emails));
                String text;
                File path;
                if (rowsValue.get("<country>").equals("Ru")) {
                    text = textRu;
                    path = textFileRu;
                } else {
                    text = textEn;
                    path = textFileEn;
                }
                // Тема письма
                message.setSubject(getTheme(path).replace("<theme>", rowsValue.get("<theme>"))); // Замените на вашу тему письма
                for (String item : rowsName) {
                    text = text.replace(item, rowsValue.get(item));
                }
                message.setContent(text, "text/html; charset=UTF-8");
                // Отправка письма
                Transport.send(message);
                workbook.close();
                excelFile.close();
            }
            i++;
        }
        clean();
    }

    public static void clean() {
        data = null;
        emails = null;
        user = null;
        password = null;
        exelFile = null;
        textFileRu = null;
        textFileEn = null;
    }

    public static String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return Double.toString(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                // В этом случае мы возвращаем формулу как строку
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static List<XWPFParagraph> getParagraphs(File file) throws IOException {

        FileInputStream docxFile = new FileInputStream(file);
        XWPFDocument document = new XWPFDocument(docxFile);

        // Получение списка параграфов
        return document.getParagraphs();
    }

    private static String getText(File file) throws IOException {
        StringBuilder documentContent = new StringBuilder();
        List<XWPFParagraph> paragraphs = getParagraphs(file);
        String theme = null;
        boolean inList = false;
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).getNumID() != null) {
                if (!inList) {
                    documentContent.append("<ul>");
                    inList = true;
                }
                documentContent.append("<li>");
            } else {
                if (inList) {
                    documentContent.append("</ul>");
                    inList = false;
                }
                documentContent.append("<p>");
            }
            for (XWPFRun run : paragraphs.get(i).getRuns()) {
                // Получение текстового содержимого и его форматирования
                String text = run.getText(0);
                if (text != null) {
                    // Добавление текстового содержимого с тегами форматирования
                    documentContent.append("<span style=\"");
                    if (run.isBold()) {
                        documentContent.append("font-weight:bold;");
                    }
                    if (run.isItalic()) {
                        documentContent.append("font-style:italic;");
                    }
                    documentContent.append("font-size:").append(run.getFontSize()).append("pt;");
                    documentContent.append("color:").append(run.getColor() != null ? run.getColor() : "black").append(";");
                    documentContent.append("font-family:").append(run.getFontFamily() != null ? run.getFontFamily() : "default").append(";");
                    documentContent.append("\">").append(text).append("</span>");
                }
                if (i == 0) theme = documentContent.toString();
            }
            if (paragraphs.get(i).getNumID() != null) {
                documentContent.append("</li>");
            } else {
                documentContent.append("</p>");
            }
        }
        if (inList) {
            documentContent.append("</ul>");
        }
        return "<html><body>" + String.valueOf(documentContent).replace(Objects.requireNonNull(theme), "") +  "</body></html>";
    }

    public static String getTheme(File file) throws IOException {
        return getParagraphs(file).getFirst().getText();
    }

    public static File getExelFile() {
        return exelFile;
    }

    public static void setExelFile(File exelFile) {
        EmailSender.exelFile = exelFile;
    }

    public static File getTextFileRu() {
        return textFileRu;
    }

    public static void setTextFileRu(File textFileRu) {
        EmailSender.textFileRu = textFileRu;
    }

    public static File getTextFileEn() {
        return textFileEn;
    }

    public static void setTextFileEn(File textFileEn) {
        EmailSender.textFileEn = textFileEn;
    }

    public static File getData() {
        return data;
    }

    public static void setData(File data) {
        EmailSender.data = data;
    }

    public static boolean isTest() {
        return test;
    }

    public static void setTest(boolean test) {
        EmailSender.test = test;
    }
}