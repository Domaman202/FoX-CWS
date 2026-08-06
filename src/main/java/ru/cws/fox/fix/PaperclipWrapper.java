package ru.cws.fox.fix;

import org.tinylog.Logger;

public class PaperclipWrapper {
    public static void exit(int status) {
        Logger.info("Folia files downloaded. Please restart");
        System.exit(status);
    }
}
