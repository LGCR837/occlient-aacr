package aoharureverie.ocaacrclient.oldchat.util;

import aoharureverie.ocaacrclient.oldchat.api.HttpUtil;

import java.net.URL;
import java.util.ArrayList;

public class MediaUrlResolver {
    private static final String DATA_ORIGIN = "http://154.8.227.219:9090";

    private MediaUrlResolver() {
    }

    public static String resolve(String url) {
        String[] candidates = resolveCandidates(url);
        if (candidates.length > 0) {
            return candidates[0];
        }
        return "";
    }

    public static String resolveFallback(String url) {
        String[] candidates = resolveCandidates(url);
        if (candidates.length > 1) {
            return candidates[1];
        }
        return "";
    }


    public static boolean isDataOriginUrl(String url) {
        if (url == null) {
            return false;
        }
        String raw = url.trim();
        if (raw.length() == 0) {
            return false;
        }
        if (!(raw.startsWith("http://") || raw.startsWith("https://"))) {
            return false;
        }
        try {
            URL parsed = new URL(raw);
            String currentOrigin = buildOrigin(parsed.getProtocol(), parsed.getHost(), parsed.getPort());
            return sameOrigin(currentOrigin, DATA_ORIGIN);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMainOriginUrl(String url) {
        if (url == null) {
            return false;
        }
        String raw = url.trim();
        if (raw.length() == 0) {
            return false;
        }
        if (!(raw.startsWith("http://") || raw.startsWith("https://"))) {
            return false;
        }
        String mainOrigin = resolveMainOrigin();
        if (mainOrigin == null || mainOrigin.length() == 0) {
            return false;
        }
        try {
            URL parsed = new URL(raw);
            String currentOrigin = buildOrigin(parsed.getProtocol(), parsed.getHost(), parsed.getPort());
            return sameOrigin(currentOrigin, mainOrigin);
        } catch (Exception e) {
            return false;
        }
    }

    public static String[] resolveCandidates(String url) {
        if (url == null) {
            return new String[0];
        }
        String raw = url.trim();
        if (raw.length() == 0) {
            return new String[0];
        }

        String mainOrigin = resolveMainOrigin();

        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return resolveAbsoluteCandidates(raw, mainOrigin);
        }

        if (raw.startsWith("/")) {
            if (isUploadPath(raw)) {
                return dedupe(new String[]{mainOrigin + raw, DATA_ORIGIN + raw});
            }
            return dedupe(new String[]{mainOrigin + raw});
        }

        if (isUploadPath(raw)) {
            String path = raw.startsWith("/") ? raw : ("/" + raw);
            return dedupe(new String[]{mainOrigin + path, DATA_ORIGIN + path});
        }

        return new String[]{raw};
    }

    private static String[] resolveAbsoluteCandidates(String raw, String mainOrigin) {
        URL parsed;
        try {
            parsed = new URL(raw);
        } catch (Exception e) {
            return new String[]{raw};
        }

        String pathAndQuery = parsed.getFile();
        if (pathAndQuery == null || pathAndQuery.length() == 0) {
            pathAndQuery = "/";
        }
        if (!isUploadPath(pathAndQuery)) {
            return new String[]{raw};
        }

        String protocol = parsed.getProtocol();
        String host = parsed.getHost();
        int port = parsed.getPort();
        String currentOrigin = buildOrigin(protocol, host, port);

        String dataOrigin = normalizeOrigin(DATA_ORIGIN);
        String normalizedMain = normalizeOrigin(mainOrigin);
        String dataCandidate = dataOrigin + pathAndQuery;
        String mainCandidate = normalizedMain + pathAndQuery;

        if (sameOrigin(currentOrigin, dataOrigin)) {
            return dedupe(new String[]{mainCandidate, raw});
        }
        if (sameOrigin(currentOrigin, normalizedMain)) {
            return dedupe(new String[]{raw, dataCandidate});
        }

        return new String[]{raw};
    }

    private static String resolveMainOrigin() {
        String base = HttpUtil.BASE_URL;
        if (base == null) {
            return "";
        }
        String trimmed = base.trim();
        int idx = trimmed.indexOf("/v1");
        if (idx > 0) {
            return normalizeOrigin(trimmed.substring(0, idx));
        }
        return normalizeOrigin(trimmed);
    }

    private static boolean isUploadPath(String path) {
        if (path == null) {
            return false;
        }
        String p = path.trim();
        return p.startsWith("/uploads/")
                || p.startsWith("/v1/uploads/")
                || p.startsWith("uploads/")
                || p.startsWith("v1/uploads/");
    }

    private static String[] dedupe(String[] input) {
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < input.length; i++) {
            String item = input[i];
            if (item == null) {
                continue;
            }
            String normalized = item.trim();
            if (normalized.length() == 0) {
                continue;
            }
            boolean exists = false;
            for (int j = 0; j < out.size(); j++) {
                if (normalized.equals(out.get(j))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                out.add(normalized);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    private static boolean sameOrigin(String a, String b) {
        return normalizeOrigin(a).equals(normalizeOrigin(b));
    }

    private static String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        String out = origin.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String buildOrigin(String protocol, String host, int port) {
        String p = protocol == null ? "http" : protocol;
        String h = host == null ? "" : host;
        if (port <= 0) {
            return normalizeOrigin(p + "://" + h);
        }
        return normalizeOrigin(p + "://" + h + ":" + port);
    }
}
