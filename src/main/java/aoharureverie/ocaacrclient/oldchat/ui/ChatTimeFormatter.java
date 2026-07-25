package aoharureverie.ocaacrclient.oldchat.ui;

public class ChatTimeFormatter {
    private ChatTimeFormatter() {
    }

    public static boolean shouldShowTime(long currentTimestamp, long prevTimestamp) {
        long currentTs = ChatMessageUtil.normalizeTimestamp(currentTimestamp);
        long prevTs = ChatMessageUtil.normalizeTimestamp(prevTimestamp);
        long diff = Math.abs(currentTs - prevTs);
        return diff > 5 * 60 * 1000;
    }

    public static String formatTime(long timestamp) {
        long normalized = ChatMessageUtil.normalizeTimestamp(timestamp);
        if (normalized <= 0) {
            return "";
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(normalized);
        java.util.Calendar now = java.util.Calendar.getInstance();

        if (isSameDay(cal, now)) {
            return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));
        } else if (isYesterday(cal, now)) {
            return "昨天 " + String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));
        } else if (isSameYear(cal, now)) {
            return String.format("%d月%d日 %02d:%02d",
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));
        } else {
            return String.format("%d年%d月%d日",
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH));
        }
    }

    private static boolean isSameDay(java.util.Calendar cal1, java.util.Calendar cal2) {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static boolean isYesterday(java.util.Calendar cal, java.util.Calendar now) {
        java.util.Calendar yesterday = (java.util.Calendar) now.clone();
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);
        return isSameDay(cal, yesterday);
    }

    private static boolean isSameYear(java.util.Calendar cal1, java.util.Calendar cal2) {
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR);
    }
}
