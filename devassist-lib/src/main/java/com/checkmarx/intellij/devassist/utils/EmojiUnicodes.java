package com.checkmarx.intellij.devassist.utils;

/**
 * The EmojiUnicodes class provides a set of Unicode constants that represent various commonly
 * used emoji symbols. These symbols can be used for UI elements, logging, messages, and other
 * text-based functionality where emoji representations are needed.
 * <p>
 * This is a utility class and is not meant to be instantiated.
 * <p>
 * All emoji constants are defined as public static final fields and are immutable.
 */
public final class EmojiUnicodes {

    private EmojiUnicodes() {
    }

    // ✅ Green check mark
    public static final String CHECK = "\u2705";

    // ❌ Red cross mark
    public static final String CROSS = "\u274C";

    // 🔒 Lock
    public static final String LOCK = "\uD83D\uDD12";

    // 🔁 Repeat Button
    public static final String REPEAT = "\uD83D\uDD01";

    // ⚠️ Warning Sign
    public static final String WARNING = "\u26A0\uFE0F";

    // 🐳 Whale
    public static final String WHALE = "\uD83D\uDC33";

    // ℹ️️ Information Source
    public static final String INFO = "\u2139\uFE0F";

    // ❗ Red Exclamation Mark
    public static final String EXCLAMATION = "\u2757";

    // 👉 Backhand Index Pointing Right
    public static final String POINT_RIGHT = "\uD83D\uDC49";

    // 🔍 Magnifying Glass Tilted Left
    public static final String SEARCH = "\uD83D\uDD0D";

    // 🧠 Brain
    public static final String BRAIN = "\uD83E\uDDE0";

    // 📋 Clipboard
    public static final String CLIPBOARD = "\uD83D\uDCCB";

    // ✏️ Pencil (emoji-style includes variation selector)
    public static final String PENCIL = "\u270F\uFE0F";

    // 🛠️ Hammer and Wrench
    public static final String TOOLS = "\uD83D\uDEE0\uFE0F";

    // 🧨 Firecracker
    public static final String FIRECRACKER = "\uD83E\uDDE8";

    // 🚨 Police Light
    public static final String POLICE_LIGHT = "\uD83D\uDEA8";

    // 🏗️ Building Construction
    public static final String CONSTRUCTION = "\uD83C\uDFD7\uFE0F";

    // 📖 Open Book
    public static final String OPEN_BOOK = "\uD83D\uDCD6";

    // 🛡️ Shield (emoji-style includes variation selector)
    public static final String SHIELD = "\uD83D\uDEE1\uFE0F";

    // 📚 Books
    public static final String BOOKS = "\uD83D\uDCDA";
}
